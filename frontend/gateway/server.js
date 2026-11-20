// API Gateway de desarrollo — implementa lo que la memoria (§3.1) asigna al Gateway:
//   · enrutamiento  /{servicio}/v1/**  →  http://localhost:{puerto}/v1/**
//   · validación del JWT emitido por Usuarios y control de rol según el contrato OpenAPI
//   · inyección de X-User-Id / X-User-Role (Usuarios las exige) y descarte de las que envíe el cliente
//   · intercambio de token para Estadísticas (RS256 + iss + aud, que es lo que ese servicio valida)
//   · limitación de peticiones
// Además sirve el frontend (../public) y expone /_gateway/health para el panel "Estado del sistema".
import express from 'express';
import { createProxyMiddleware } from 'http-proxy-middleware';
import { jwtVerify, SignJWT } from 'jose';
import { generateKeyPairSync, createPrivateKey } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { RULES, RANK, ROLE_RANK, SERVICES } from './rules.js';

const here = dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT || 8080);
const HOST = process.env.UPSTREAM_HOST || 'localhost';
const JWT_SECRET = process.env.JWT_SECRET;               // el MISMO que usa Usuarios
const WS_URL = process.env.WS_URL || `ws://localhost:${SERVICES.estadisticas.port}/v1/ws`;
const RATE_PER_MIN = Number(process.env.RATE_PER_MIN || 600);
const STATS_ISSUER = process.env.STATS_JWT_ISSUER || 'asee-local';
const STATS_AUDIENCE = process.env.STATS_JWT_AUDIENCE || 'estadisticas';
// 'exchange' (por defecto): reemite un RS256 con iss/aud para Estadísticas configurada con JWT_PUBLIC_KEY.
// 'passthrough': reenvía el token HS256 de Usuarios; úsalo si Estadísticas se arranca con JWT_SECRET.
const STATS_MODE = process.env.STATS_TOKEN_MODE || 'exchange';
const SUPABASE_URL = (process.env.SUPABASE_URL || '').replace(/\/+$/, '');
const SUPABASE_ANON_KEY = process.env.SUPABASE_ANON_KEY || '';

if (!JWT_SECRET) {
  console.error('\n✖ Falta JWT_SECRET (el mismo valor que usa el microservicio Usuarios).\n' +
                '  Ejemplo:  JWT_SECRET=<igual que en Usuarios> npm start\n');
  process.exit(1);
}
const secretKey = new TextEncoder().encode(JWT_SECRET);   // Usuarios usa secret.getBytes(UTF_8)

// ── Claves RSA para el token de Estadísticas ───────────────────────────────
const keysDir = resolve(here, 'keys');
const privPath = resolve(keysDir, 'private.pem'), pubPath = resolve(keysDir, 'public.pem');
if (!existsSync(privPath)) {
  mkdirSync(keysDir, { recursive: true });
  const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
  writeFileSync(privPath, privateKey.export({ type: 'pkcs8', format: 'pem' }));
  writeFileSync(pubPath, publicKey.export({ type: 'spki', format: 'pem' }));
}
const statsKey = createPrivateKey(readFileSync(privPath));

// ── Utilidades ─────────────────────────────────────────────────────────────
const error = (res, codigo, mensaje) => res.status(codigo).json({ codigo, mensaje });
const hits = new Map();
setInterval(() => hits.clear(), 60_000).unref();

function findRule(svc, method, subpath) {
  const p = subpath.split('?')[0];
  return RULES.find(r => r.svc === svc && r.method === method && r.re.test(p));
}

async function identify(req) {
  const h = req.headers.authorization || '';
  if (!h.startsWith('Bearer ')) return null;
  try {
    const { payload } = await jwtVerify(h.slice(7), secretKey, { algorithms: ['HS256', 'HS384', 'HS512'] });
    const rol = String(payload.rol || payload.role || 'visitante').toLowerCase();
    return { id: payload.sub, rol };
  } catch { return null; }
}

const app = express();
app.disable('x-powered-by');

app.use((req, res, next) => {                                   // rate limiting
  const n = (hits.get(req.ip) || 0) + 1; hits.set(req.ip, n);
  if (n > RATE_PER_MIN) return error(res, 429, 'Demasiadas peticiones; espera un minuto');
  next();
});

app.get('/config.json', (_req, res) => res.json({ gateway: '', wsUrl: WS_URL, supabaseUrl: SUPABASE_URL, supabaseKey: SUPABASE_ANON_KEY }));

// ── Estado de los servicios (contrato) ─────────────────────────────────────
const PROBES = {
  usuarios:      { path: '/v1/auth/login', expect: [405, 400, 200] },        // GET sobre un POST → 405
  deportivo:     { path: '/v1/partidos?tamano=1', expect: [200] },
  entradas:      { path: '/v1/partidos/20000000-0000-0000-0000-000000000004/aforo', expect: [200] },
  compras:       { path: '/v1/productos', expect: [200] },
  estadisticas:  { path: '/v1/temporadas/2026-2027/estadisticas', expect: [200] },
  institucional: { path: '/v1/club/identidad', expect: [200] },
};
app.get('/_gateway/health', async (_req, res) => {
  const out = {};
  await Promise.all(Object.entries(PROBES).map(async ([svc, pr]) => {
    const t0 = Date.now();
    try {
      const r = await fetch(`http://${HOST}:${SERVICES[svc].port}${pr.path}`, { signal: AbortSignal.timeout(2500) });
      out[svc] = { estado: pr.expect.includes(r.status) ? 'ok' : 'contrato', http: r.status, ms: Date.now() - t0, sonda: pr.path };
    } catch (e) {
      out[svc] = { estado: 'caido', http: 0, ms: Date.now() - t0, sonda: pr.path, detalle: e.cause?.code || e.name };
    }
  }));
  res.json(out);
});

// ── Enrutado + autorización ────────────────────────────────────────────────
for (const [svc, { port }] of Object.entries(SERVICES)) {
  const proxy = createProxyMiddleware({
    target: `http://${HOST}:${port}`, changeOrigin: true, xfwd: true,
    pathRewrite: p => (p.startsWith(`/${svc}/v1`) ? p.slice(`/${svc}`.length) : '/v1' + p),
    on: { error: (_e, _req, res) => res.writeHead?.(503, { 'Content-Type': 'application/json' }) &&
                                    res.end(JSON.stringify({ codigo: 503, mensaje: `El servicio ${svc} no responde en el puerto ${port}` })) },
  });

  app.use(`/${svc}/v1`, async (req, res, next) => {
    delete req.headers['x-user-id']; delete req.headers['x-user-role'];       // anti-suplantación
    const sub = req.path;
    const rule = findRule(svc, req.method, sub);
    if (req.method === 'OPTIONS') return next();
    if (!rule) return error(res, 404, `Operación no definida en el contrato de ${svc}: ${req.method} ${sub}`);

    const who = await identify(req);
    if (rule.level !== 'public') {
      if (!who) return error(res, 401, 'Token ausente, caducado o no válido');
      if (rule.level === 'owner') {
        const target = sub.split('/')[2];
        if (target !== who.id && (ROLE_RANK[who.rol] || 0) < RANK.staff) return error(res, 403, 'Solo puedes acceder a tus propios datos');
      } else if ((ROLE_RANK[who.rol] || 0) < RANK[rule.level]) {
        return error(res, 403, rule.level === 'admin' ? 'Se requiere rol admin' : 'Se requiere rol staff o admin');
      }
    }
    if (who) {
      req.headers['x-user-id'] = who.id;
      req.headers['x-user-role'] = who.rol;
      if (svc === 'estadisticas' && STATS_MODE === 'exchange') {              // intercambio de token
        const token = await new SignJWT({ rol: who.rol })
          .setProtectedHeader({ alg: 'RS256' }).setSubject(who.id).setIssuer(STATS_ISSUER)
          .setAudience(STATS_AUDIENCE).setIssuedAt().setExpirationTime('5m').sign(statsKey);
        req.headers.authorization = `Bearer ${token}`;
      }
    }
    next();
  }, proxy);
}

app.use(express.static(resolve(here, '../public')));
app.use((_req, res) => error(res, 404, 'Ruta no encontrada'));

app.listen(PORT, () => {
  console.log(`\n▶ Gateway + frontend en http://localhost:${PORT}`);
  console.log(`  Servicios: ${Object.entries(SERVICES).map(([s, v]) => `${s}:${v.port}`).join('  ')}`);
  if (STATS_MODE === 'exchange') {
    console.log(`  Estadísticas (modo exchange) →  JWT_PUBLIC_KEY=file:${pubPath.replace(/\\/g, '/')}`);
    console.log(`  (con JWT_ISSUER=${STATS_ISSUER} y JWT_AUDIENCE=${STATS_AUDIENCE})  — o bien arranca Estadísticas con JWT_SECRET y usa STATS_TOKEN_MODE=passthrough\n`);
  } else console.log('  Estadísticas (modo passthrough): debe arrancar con JWT_SECRET igual que Usuarios\n');
});
