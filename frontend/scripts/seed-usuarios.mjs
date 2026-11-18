#!/usr/bin/env node
// Crea los usuarios de prueba contra el backend REAL a través del gateway (idempotente):
//   staff@club.test (rol staff) y socio@club.test (rol socio con cuota anual), clave Club2026!
// Requiere un admin existente: arranca Usuarios con ADMIN_EMAIL y ADMIN_PASSWORD y pásalos aquí.
const GW = process.env.GATEWAY || 'http://localhost:8080';
const ADMIN = { email: process.env.ADMIN_EMAIL || 'admin@club.test', password: process.env.ADMIN_PASSWORD || 'Club2026!' };
const PASS = process.env.SEED_PASSWORD || 'Club2026!';
const call = async (m, p, token, body) => {
  const r = await fetch(`${GW}/usuarios/v1${p}`, { method: m, headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const t = await r.text(); return { status: r.status, json: t ? JSON.parse(t) : null };
};
const l = await call('POST', '/auth/login', null, ADMIN);
if (l.status !== 200) { console.error(`✖ No se pudo entrar como admin (${ADMIN.email}): HTTP ${l.status} ${JSON.stringify(l.json)}`); process.exit(1); }
const tk = l.json.token;
for (const [email, nombre, rol] of [['staff@club.test', 'Manuel Rubio', 'staff'], ['socio@club.test', 'Lucía Herrera', 'socio']]) {
  const reg = await call('POST', '/usuarios', null, { nombre, email, password: PASS });
  if (![201, 409].includes(reg.status)) { console.error(`✖ registro de ${email}: HTTP ${reg.status} ${JSON.stringify(reg.json)}`); process.exit(1); }
  const lista = (await call('GET', '/usuarios?tamano=100', tk)).json || [];
  const u = lista.find(x => x.email === email); if (!u) { console.error(`✖ ${email} no aparece en GET /usuarios`); process.exit(1); }
  const r = await call('PUT', `/usuarios/${u.usuarioId}/rol`, tk, { rol }); if (r.status !== 200) { console.error(`✖ rol de ${email}: HTTP ${r.status}`); process.exit(1); }
  if (rol === 'socio') await call('PUT', `/usuarios/${u.usuarioId}/socio`, tk, { tipoCuota: 'anual', fechaCaducidad: new Date(Date.now() + 365 * 864e5).toISOString().slice(0, 10) });
  console.log(`✔ ${email} → ${rol}`);
}
