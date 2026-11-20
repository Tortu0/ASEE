// Mock de los seis microservicios, FIEL AL CONTRATO (Endpoints corregidos/*.yaml), no a la implementación actual.
// Sirve para (a) desarrollar/demostrar el frontend sin backend y (b) comprobar que scripts/verify-backend.mjs
// pasa contra algo que cumple el contrato. Estado en memoria; mismos UUID semilla que Deportivo/Institucional.
// Debe ejecutarse detrás del gateway: la identidad llega en X-User-Id / X-User-Role.
import express from 'express';
import http from 'node:http';
import { randomUUID as uuid } from 'node:crypto';
import { SignJWT } from 'jose';
import { WebSocketServer } from 'ws';

const SECRET = new TextEncoder().encode(process.env.JWT_SECRET || 'club-demo-secret-club-demo-secret-0001');
const now = () => new Date().toISOString();
const dayMs = 86_400_000;
const inDays = (d, h = 18, m = 30) => { const t = new Date(Date.now() + d * dayMs); t.setUTCHours(h, m, 0, 0); return t.toISOString(); };
const id = (pre, n) => `${pre}-0000-0000-0000-${String(n).padStart(12, '0')}`;
const err = (res, codigo, mensaje, detalles) => res.status(codigo).json({ codigo, mensaje, ...(detalles ? { detalles } : {}) });
const me = req => req.headers['x-user-id'] ? { id: req.headers['x-user-id'], rol: req.headers['x-user-role'] } : null;
const isStaff = u => u && ['staff', 'admin'].includes(u.rol);
const page = (list, q) => { const p = Math.max(1, +q.pagina || 1), t = Math.min(100, Math.max(1, +q.tamano || 20)); return list.slice((p - 1) * t, p * t); };
const wrap = fn => (req, res) => { try { return fn(req, res); } catch (e) { console.error(e); err(res, 500, 'Error interno del mock'); } };

function service(name, port, routes, hook) {
  const app = express();
  app.use(express.json());
  const r = express.Router();
  routes(r);
  app.use(process.env.MOCK_LEGACY && name === 'compras' ? '/api' : '/v1', r);   // MOCK_LEGACY reproduce fallos reales del código
  app.use((req, res) => err(res, 404, `No existe ${req.method} ${req.path}`));
  const server = http.createServer(app);
  hook?.(server);
  server.listen(port, () => console.log(`  mock ${name.padEnd(13)} :${port}/v1`));
}

// ═════════════ USUARIOS ═════════════
const U = { usuarios: [], socios: new Map(), actividades: [] };
const addUser = (nombre, email, rol, extra = {}) => {
  const u = { usuarioId: uuid(), nombre, email, password: 'Club2026!', rol, telefono: null, direccion: null, fechaAlta: now(), ...extra };
  U.usuarios.push(u); return u;
};
addUser('Ana García', 'admin@club.test', 'admin');
addUser('Manuel Rubio', 'staff@club.test', 'staff');
const socio = addUser('Lucía Herrera', 'socio@club.test', 'socio', { direccion: 'Calle Mayor 1, Madroñera' });
addUser('Pedro Visitante', 'visitante@club.test', 'visitante');
U.socios.set(socio.usuarioId, { usuarioId: socio.usuarioId, numeroSocio: 'S-' + socio.usuarioId.slice(0, 6).toUpperCase(), estado: 'activo', tipoCuota: 'anual', fechaAlta: '2025-09-01', fechaCaducidad: inDays(200).slice(0, 10), descuentoPorcentaje: 15 });
const pub = ({ password, ...u }) => u;
const socioDe = uid => U.socios.get(uid) || { usuarioId: uid, estado: 'no_socio', descuentoPorcentaje: 0 };
const descuentoDe = uid => { const s = socioDe(uid); return s.estado === 'activo' ? s.descuentoPorcentaje : 0; };
const registrarActividad = (usuarioId, tipo, referenciaId, importe, descripcion) => {   // = consumo del evento por Usuarios
  if (process.env.MOCK_LEGACY) return;                                                  // evento perdido (cola sin binding)
  if (U.actividades.some(a => a.usuarioId === usuarioId && a.tipo === tipo && a.referenciaId === referenciaId)) return;
  U.actividades.push({ usuarioId, tipo, referenciaId, descripcion, importe, fecha: now() });
};

service('usuarios', 8081, r => {
  r.get('/auth/login', (_q, res) => err(res, 405, 'Método no permitido'));
  r.post('/auth/login', wrap(async (req, res) => {
    const u = U.usuarios.find(x => x.email === req.body?.email);
    if (!u || u.password !== req.body?.password) return err(res, 401, 'Email o contraseña incorrectos');
    const token = await new SignJWT({ email: u.email, rol: u.rol }).setProtectedHeader({ alg: 'HS256' })
      .setSubject(u.usuarioId).setIssuedAt().setExpirationTime('1h').sign(SECRET);
    res.json({ token, tipo: 'Bearer', expiraEn: 3600, usuario: pub(u) });
  }));
  r.post('/usuarios', wrap((req, res) => {
    const b = req.body || {};
    if (!b.nombre || !b.email || !b.password) return err(res, 400, 'nombre, email y password son obligatorios');
    if (U.usuarios.some(x => x.email === b.email)) return err(res, 409, 'El email ya está registrado');
    const u = addUser(b.nombre, b.email, 'visitante', { password: b.password, telefono: b.telefono ?? null, direccion: b.direccion ?? null });
    res.status(201).location(`/v1/usuarios/${u.usuarioId}`).json(pub(u));
  }));
  r.get('/usuarios', wrap((req, res) => {
    if (me(req)?.rol !== 'admin') return err(res, 403, 'Solo admin');
    res.json(page(U.usuarios.filter(u => !req.query.rol || u.rol === req.query.rol).map(pub), req.query));
  }));
  const own = (req, res, admin = false) => {
    const u = me(req);
    if (!u) { err(res, 401, 'No autenticado'); return false; }
    if (u.id !== req.params.id && !(admin ? u.rol === 'admin' : isStaff(u))) { err(res, 403, 'No tienes permisos sobre este usuario'); return false; }
    return true;
  };
  const find = (req, res) => U.usuarios.find(x => x.usuarioId === req.params.id) || (err(res, 404, 'Usuario no encontrado'), null);
  r.get('/usuarios/:id', wrap((req, res) => { if (own(req, res)) { const u = find(req, res); u && res.json(pub(u)); } }));
  r.put('/usuarios/:id', wrap((req, res) => {
    if (!own(req, res, true)) return; const u = find(req, res); if (!u) return;
    const b = req.body || {}; if (!Object.keys(b).length) return err(res, 400, 'El cuerpo no puede estar vacío');
    for (const k of ['nombre', 'telefono', 'direccion']) if (b[k] !== undefined) u[k] = b[k];
    res.json(pub(u));
  }));
  r.delete('/usuarios/:id', wrap((req, res) => {
    if (me(req)?.rol !== 'admin') return err(res, 403, 'Solo admin');
    const i = U.usuarios.findIndex(x => x.usuarioId === req.params.id); if (i < 0) return err(res, 404, 'Usuario no encontrado');
    U.usuarios.splice(i, 1); res.status(204).end();
  }));
  r.put('/usuarios/:id/rol', wrap((req, res) => {
    if (me(req)?.rol !== 'admin') return err(res, 403, 'Solo admin');
    const u = find(req, res); if (!u) return;
    if (!['visitante', 'socio', 'staff', 'admin'].includes(req.body?.rol)) return err(res, 400, 'rol no válido');
    u.rol = req.body.rol; res.json(pub(u));
  }));
  r.get('/usuarios/:id/socio', wrap((req, res) => { if (own(req, res) && find(req, res)) res.json(socioDe(req.params.id)); }));
  r.put('/usuarios/:id/socio', wrap((req, res) => {
    if (!isStaff(me(req))) return err(res, 403, 'Solo staff o admin');
    const u = find(req, res); if (!u) return;
    const { tipoCuota, fechaCaducidad } = req.body || {};
    if (!tipoCuota || !fechaCaducidad) return err(res, 400, 'tipoCuota y fechaCaducidad son obligatorios');
    const prev = U.socios.get(u.usuarioId);
    const s = { usuarioId: u.usuarioId, numeroSocio: prev?.numeroSocio || 'S-' + u.usuarioId.slice(0, 6).toUpperCase(), estado: 'activo', tipoCuota, fechaAlta: prev?.fechaAlta || now().slice(0, 10), fechaCaducidad, descuentoPorcentaje: 15 };
    U.socios.set(u.usuarioId, s); if (u.rol === 'visitante') u.rol = 'socio';
    res.json(s);
  }));
  r.get('/usuarios/:id/historial', wrap((req, res) => {
    if (!own(req, res) || !find(req, res)) return;
    res.json(U.actividades.filter(a => a.usuarioId === req.params.id).sort((a, b) => b.fecha.localeCompare(a.fecha)).map(({ usuarioId, ...a }) => a));
  }));
  r.post('/usuarios/:id/historial', wrap((req, res) => {
    if (me(req)?.rol !== 'admin') return err(res, 403, 'Solo admin');
    if (!find(req, res)) return;
    const { tipo, referenciaId, fecha } = req.body || {};
    if (!['entrada', 'pedido'].includes(tipo) || !referenciaId || !fecha) return err(res, 400, 'tipo, referenciaId y fecha son obligatorios');
    if (U.actividades.some(a => a.usuarioId === req.params.id && a.tipo === tipo && a.referenciaId === referenciaId)) return err(res, 409, 'La actividad ya existe');
    registrarActividad(req.params.id, tipo, referenciaId, req.body.importe, req.body.descripcion);
    res.status(201).json(req.body);
  }));
});

// ═════════════ DEPORTIVO ═════════════
const J = [['Carlos Gómez', 4, 'base', 185], ['Pablo Sánchez', 7, 'escolta', 190], ['Luis Pérez', 9, 'alero', 198], ['Diego Martín', 11, 'alero', 196],
  ['Javier Ramos', 13, 'ala_pivot', 203], ['Álvaro Moreno', 15, 'pivot', 208], ['Marcos Ortega', 5, 'base', 180], ['Hugo Blanco', 8, 'escolta', 188], ['Iván Castro', 10, 'alero', 194]]
  .map(([nombre, dorsal, posicion, alturaCm], i) => ({ jugadorId: id('10000000', i + 1), nombre, dorsal, posicion, alturaCm, fechaNacimiento: `${1996 + (i % 8)}-0${1 + (i % 9)}-1${i % 9}`, fotoUrl: null, activo: i < 8 }));
const T = '2026-2027', COMP = 'Liga regional', PAB = 'Pabellón Municipal de Madroñera';
const P = [
  ['CB Navalmoral', PAB, true, 1, -30, 'finalizado', 78, 71, false], ['CB Coria', 'Pabellón Municipal de Coria', false, 2, -23, 'finalizado', 65, 70, false],
  ['Plasencia Basket', PAB, true, 3, -16, 'finalizado', 82, 80, false], ['CB Jaraíz', PAB, true, 4, 3, 'programado', null, null, true],
  ['Trujillo Basket', 'Pabellón Municipal de Trujillo', false, 5, 0, 'en_juego', 41, 38, false], ['CB Moraleja', PAB, true, 6, 11, 'aplazado', null, null, false],
  ['CB Cáceres Joven', PAB, true, 7, 18, 'programado', null, null, true],
].map(([rival, lugar, esLocal, jornada, d, estado, pl, pv, venta]) => ({ partidoId: id('20000000', jornada), fechaHora: d === 0 ? new Date(Date.now() - 3_000_000).toISOString() : inDays(d), rival, lugar, esLocal, temporada: T, competicion: COMP, jornada, estado, puntosLocal: pl, puntosVisitante: pv, ventaEntradasAbierta: venta }));
const N = [
  ['El club incorpora al base Marcos Ortega', 'El club ha cerrado el fichaje del base Marcos Ortega, que llega para reforzar la rotación exterior esta temporada.', 'fichaje', [7], -42],
  ['Victoria en el debut de liga frente al CB Navalmoral', 'El equipo se impuso por 78-71 en el Pabellón Municipal, con un gran partido de Carlos Gómez y Luis Pérez.', 'cronica', [1, 3], -29],
  ['Javier Ramos, baja dos semanas por un esguince', 'El ala-pívot Javier Ramos se perderá los próximos encuentros por un esguince de tobillo.', 'lesion', [5], -15],
  ['Convocatoria para el partido ante el CB Jaraíz', 'El cuerpo técnico ha convocado a doce jugadores para el choque del sábado en casa.', 'convocatoria', [1, 2, 3, 4], -2],
].map(([titulo, contenido, categoria, js, d], i) => ({ noticiaId: id('30000000', i + 1), titulo, contenido, categoria, jugadoresRelacionados: js.map(n => id('10000000', n)), imagenUrl: null, autorId: null, fechaPublicacion: inDays(d, 10, 0) }));
const prefs = new Map();
const dep = { J, P, N };

function crud(r, base, list, key, { make, filters = () => true, delRole = 'admin' }) {
  r.get(base, wrap((req, res) => res.json(page(list.filter(x => filters(x, req.query)), req.query))));
  r.post(base, wrap((req, res) => {
    const e = make(req.body || {}); if (typeof e === 'string') return err(res, 400, e);
    list.push(e); res.status(201).location(`/v1${base}/${e[key]}`).json(e);
  }));
  r.get(`${base}/:id`, wrap((req, res) => { const e = list.find(x => x[key] === req.params.id); e ? res.json(e) : err(res, 404, 'Recurso no encontrado'); }));
  r.put(`${base}/:id`, wrap((req, res) => {
    const i = list.findIndex(x => x[key] === req.params.id); if (i < 0) return err(res, 404, 'Recurso no encontrado');
    const e = make(req.body || {}, list[i]); if (typeof e === 'string') return err(res, 400, e);
    list[i] = { ...e, [key]: req.params.id }; res.json(list[i]);
  }));
  r.delete(`${base}/:id`, wrap((req, res) => {
    const i = list.findIndex(x => x[key] === req.params.id); if (i < 0) return err(res, 404, 'Recurso no encontrado');
    list.splice(i, 1); res.status(204).end();
  }));
}

service('deportivo', 8082, r => {
  crud(r, '/partidos', P, 'partidoId', {
    filters: (p, q) => (!q.estado || p.estado === q.estado) && (q.ventaAbierta === undefined || String(p.ventaEntradasAbierta) === q.ventaAbierta) && (!q.desde || p.fechaHora >= q.desde) && (!q.hasta || p.fechaHora <= q.hasta + 'T23:59:59Z'),
    make: (b, o = {}) => (!b.fechaHora || !b.rival || !b.lugar || !/^\d{4}-\d{4}$/.test(b.temporada || '')) ? 'fechaHora, rival, lugar y temporada (AAAA-AAAA) son obligatorios'
      : { partidoId: uuid(), esLocal: true, competicion: null, jornada: null, estado: 'programado', puntosLocal: null, puntosVisitante: null, ventaEntradasAbierta: false, ...o, ...b },
  });
  crud(r, '/jugadores', J, 'jugadorId', {
    filters: (j, q) => j.activo === (q.activo === undefined ? true : q.activo === 'true') && (!q.posicion || j.posicion === q.posicion),
    make: (b, o = {}) => (!b.nombre || !b.dorsal || !b.posicion) ? 'nombre, dorsal y posicion son obligatorios'
      : { jugadorId: uuid(), alturaCm: null, fechaNacimiento: null, fotoUrl: null, activo: true, ...o, ...b },
  });
  crud(r, '/noticias', N, 'noticiaId', {
    filters: (n, q) => (!q.categoria || n.categoria === q.categoria) && (!q.jugadorId || n.jugadoresRelacionados.includes(q.jugadorId)),
    make: (b, o = {}) => (!b.titulo || !b.contenido || !b.categoria) ? 'titulo, contenido y categoria son obligatorios'
      : { noticiaId: uuid(), jugadoresRelacionados: [], imagenUrl: null, autorId: null, fechaPublicacion: now(), ...o, ...b },
  });
  // El orden de los listados: las noticias más recientes primero
  r.get('/usuarios/:id/preferencias-notificacion', wrap((req, res) => res.json(prefs.get(req.params.id) || { usuarioId: req.params.id, jugadoresFavoritos: [], categoriasFavoritas: [], avisarInicioPartido: true })));
  r.put('/usuarios/:id/preferencias-notificacion', wrap((req, res) => {
    const p = { jugadoresFavoritos: [], categoriasFavoritas: [], avisarInicioPartido: true, ...req.body, usuarioId: req.params.id };
    prefs.set(req.params.id, p); res.json(p);
  }));
});

// ═════════════ ENTRADAS ═════════════
const E = { asientos: [], tarifas: [], reservas: [], entradas: [] };
const PRECIOS = { A: { general: 8.5, socio: 6, vip: 20 }, B: { general: 6.5, socio: 4.5, vip: 15 } };
for (const n of [4, 7]) {
  const pid = id('20000000', n);
  for (const zona of ['A', 'B']) {
    for (let fila = 1; fila <= 10; fila++) for (let numero = 1; numero <= 30; numero++) E.asientos.push({ asientoId: uuid(), partidoId: pid, zona, fila, numero, estado: 'libre' });
    for (const [tipoEntrada, precio] of Object.entries(PRECIOS[zona])) E.tarifas.push({ partidoId: pid, zona, tipoEntrada, precio });
  }
}
const asDto = ({ partidoId, ...a }) => a;
const liberarExpiradas = () => E.reservas.filter(x => x.estado === 'activa' && new Date(x.expiraEn) < new Date()).forEach(x => {
  x.estado = 'expirada'; E.asientos.filter(a => x.asientoIds.includes(a.asientoId) && a.estado === 'reservado').forEach(a => a.estado = 'libre');
});
const entradaDto = e => { const a = E.asientos.find(x => x.asientoId === e.asientoId); const { asientoId, ...rest } = e; return { ...rest, asiento: asDto(a) }; };

service('entradas', 8083, r => {
  const exists = (req, res) => E.asientos.some(a => a.partidoId === req.params.pid) || (err(res, 404, 'No existe ningún partido con ese identificador en este servicio'), false);
  r.get('/partidos/:pid/asientos', wrap((req, res) => {
    if (!exists(req, res)) return; liberarExpiradas();
    res.json(E.asientos.filter(a => a.partidoId === req.params.pid && (!req.query.zona || a.zona === req.query.zona) && (!req.query.estado || a.estado === req.query.estado)).map(asDto));
  }));
  r.get('/partidos/:pid/aforo', wrap((req, res) => {
    if (!exists(req, res)) return; liberarExpiradas();
    const s = E.asientos.filter(a => a.partidoId === req.params.pid), c = e => s.filter(a => a.estado === e).length;
    res.json({ partidoId: req.params.pid, capacidadTotal: s.length, ocupados: c('ocupado'), reservados: c('reservado'), libres: c('libre') });
  }));
  r.get('/partidos/:pid/precios', wrap((req, res) => exists(req, res) && res.json(E.tarifas.filter(t => t.partidoId === req.params.pid).map(({ partidoId, ...t }) => t))));

  r.post('/reservas', wrap((req, res) => {
    const u = me(req), { partidoId, asientos } = req.body || {};
    const partido = P.find(p => p.partidoId === partidoId);
    if (!partido) return err(res, 404, 'El partido no existe en Deportivo');
    if (!partido.ventaEntradasAbierta) return err(res, 409, 'La venta de entradas para este partido está cerrada');
    if (!Array.isArray(asientos) || !asientos.length || asientos.length > 10) return err(res, 400, 'Indica entre 1 y 10 asientos');
    if (new Set(asientos).size !== asientos.length) return err(res, 400, 'La lista de asientos contiene duplicados');
    liberarExpiradas();
    const list = asientos.map(a => E.asientos.find(x => x.asientoId === a && x.partidoId === partidoId));
    if (list.includes(undefined)) return err(res, 404, 'Algún asiento no existe en este partido');
    if (list.some(a => a.estado !== 'libre')) return err(res, 409, 'Algún asiento ya no está libre');
    list.forEach(a => a.estado = 'reservado');
    const rv = { reservaId: uuid(), partidoId, usuarioId: u.id, asientoIds: asientos, estado: 'activa', expiraEn: new Date(Date.now() + 600_000).toISOString() };
    E.reservas.push(rv);
    res.status(201).json({ reservaId: rv.reservaId, partidoId, usuarioId: u.id, asientos: list.map(asDto), expiraEn: rv.expiraEn });
  }));
  const reserva = (req, res) => {
    const rv = E.reservas.find(x => x.reservaId === req.params.id); if (!rv) return err(res, 404, 'Reserva no encontrada'), null;
    const u = me(req); if (rv.usuarioId !== u.id && !isStaff(u)) return err(res, 403, 'No eres el titular de la reserva'), null; return rv;
  };
  r.get('/reservas/:id', wrap((req, res) => { const rv = reserva(req, res); rv && res.json({ reservaId: rv.reservaId, partidoId: rv.partidoId, usuarioId: rv.usuarioId, asientos: E.asientos.filter(a => rv.asientoIds.includes(a.asientoId)).map(asDto), expiraEn: rv.expiraEn }); }));
  r.delete('/reservas/:id', wrap((req, res) => {
    const rv = reserva(req, res); if (!rv) return;
    if (rv.estado === 'pagada') return err(res, 409, 'La reserva ya está pagada; anula las entradas individualmente');
    if (rv.estado === 'activa') { rv.estado = 'cancelada'; E.asientos.filter(a => rv.asientoIds.includes(a.asientoId)).forEach(a => a.estado = 'libre'); }
    res.status(204).end();
  }));
  r.post('/entradas', wrap((req, res) => {
    const u = me(req), { reservaId, tipoEntrada, metodoPago } = req.body || {};
    if (!reservaId || !['general', 'socio', 'vip'].includes(tipoEntrada) || !['tarjeta', 'bizum', 'paypal'].includes(metodoPago)) return err(res, 400, 'reservaId, tipoEntrada y metodoPago son obligatorios y deben ser válidos');
    const rv = E.reservas.find(x => x.reservaId === reservaId); if (!rv) return err(res, 404, 'Reserva no encontrada');
    if (rv.usuarioId !== u.id) return err(res, 403, 'Solo el titular de la reserva puede comprar');
    if (rv.estado === 'pagada') return res.status(201).json(E.entradas.filter(e => e.reservaId === reservaId).map(entradaDto));
    if (rv.estado === 'cancelada') return err(res, 409, 'La reserva ha sido cancelada');
    if (rv.estado === 'expirada' || new Date(rv.expiraEn) < new Date()) { rv.estado = 'expirada'; liberarExpiradas(); return err(res, 410, 'La reserva ha caducado'); }
    const desc = descuentoDe(u.id);
    if (tipoEntrada === 'socio' && socioDe(u.id).estado !== 'activo') return err(res, 400, 'El usuario no es socio activo; no puede comprar entrada de tipo socio');
    const out = rv.asientoIds.map(aid => {
      const a = E.asientos.find(x => x.asientoId === aid); a.estado = 'ocupado';
      const base = E.tarifas.find(t => t.partidoId === rv.partidoId && t.zona === a.zona && t.tipoEntrada === tipoEntrada).precio;
      const e = { entradaId: uuid(), reservaId, partidoId: rv.partidoId, usuarioId: u.id, asientoId: aid, tipoEntrada, precioPagado: +(base * (1 - desc / 100)).toFixed(2), descuentoAplicado: desc, metodoPago, codigoQr: 'CBM-' + uuid().slice(0, 13).toUpperCase(), estado: 'valida', fechaCompra: now() };
      E.entradas.push(e);
      registrarActividad(u.id, 'entrada', e.entradaId, e.precioPagado, `Entrada zona ${a.zona}, fila ${a.fila}, asiento ${a.numero}`);   // evento "entrada comprada"
      return e;
    });
    rv.estado = 'pagada'; res.status(201).json(out.map(entradaDto));
  }));
  r.get('/entradas', wrap((req, res) => {
    const u = me(req), q = req.query; if (!q.usuarioId) return err(res, 400, 'usuarioId es obligatorio');
    if (q.usuarioId !== u.id && !isStaff(u)) return err(res, 403, 'Solo puedes consultar tus entradas');
    res.json(E.entradas.filter(e => e.usuarioId === q.usuarioId && (!q.partidoId || e.partidoId === q.partidoId)).map(entradaDto));
  }));
  r.get('/entradas/:id', wrap((req, res) => {
    const e = E.entradas.find(x => x.entradaId === req.params.id); if (!e) return err(res, 404, 'Entrada no encontrada');
    if (e.usuarioId !== me(req).id && !isStaff(me(req))) return err(res, 403, 'No es tu entrada'); res.json(entradaDto(e));
  }));
  r.delete('/entradas/:id', wrap((req, res) => {
    if (!isStaff(me(req))) return err(res, 403, 'Solo staff o admin');
    const e = E.entradas.find(x => x.entradaId === req.params.id); if (!e) return err(res, 404, 'Entrada no encontrada');
    if (e.estado === 'usada') return err(res, 409, 'La entrada ya se ha usado');
    if (e.estado === 'valida') { e.estado = 'anulada'; const a = E.asientos.find(x => x.asientoId === e.asientoId); a.estado = 'libre'; }
    res.status(204).end();
  }));
  r.post('/entradas/:id/validacion', wrap((req, res) => {
    if (!isStaff(me(req))) return err(res, 403, 'Solo staff');
    const e = E.entradas.find(x => x.entradaId === req.params.id || x.codigoQr === req.params.id); if (!e) return err(res, 404, 'Entrada no encontrada');
    if (e.estado !== 'valida') return err(res, 409, `La entrada está ${e.estado}`);
    e.estado = 'usada'; res.json(entradaDto(e));
  }));
});

// ═════════════ COMPRAS ═════════════
const talla = (...u) => ['S', 'M', 'L', 'XL'].map((t, i) => ({ talla: t, unidades: u[i] ?? 0 }));
const PR = [
  ['Camiseta oficial 2026-27', 'Primera equipación del club, tejido técnico transpirable.', 'camiseta', 49.95, talla(10, 15, 8, 0), null],
  ['Camiseta Carlos Gómez #4', 'Réplica con nombre y dorsal del base.', 'camiseta', 54.9, talla(5, 6, 4, 2), id('10000000', 1)],
  ['Bufanda del club', 'Punto doble, azul y blanca.', 'bufanda', 14.5, [{ talla: 'Única', unidades: 40 }], null],
  ['Gorra CB Madroñera', 'Visera curva con escudo bordado.', 'gorra', 19.9, [{ talla: 'Única', unidades: 25 }], id('50000000', 1)],
  ['Balón oficial talla 7', 'Balón de juego para interior.', 'accesorio', 32, [{ talla: 'Única', unidades: 12 }], id('50000000', 2)],
  ['Botella térmica', 'Acero inoxidable, 750 ml.', 'otro', 12, [{ talla: 'Única', unidades: 0 }], null],
].map(([nombre, descripcion, categoria, precio, stock, ref], i) => ({ productoId: id('90000000', i + 1), nombre, descripcion, categoria, precio, stock, fotos: [], jugadorId: ref?.startsWith('1') ? ref : undefined, patrocinadorId: ref?.startsWith('5') ? ref : undefined }));
const C = { carritos: new Map(), pedidos: [], claves: new Map() };
const carritoDe = uid => { if (!C.carritos.has(uid)) C.carritos.set(uid, []); return C.carritos.get(uid); };
const totales = (uid, items) => {
  const subtotal = +items.reduce((s, i) => s + i.precioUnitario * i.cantidad, 0).toFixed(2), pct = descuentoDe(uid);
  const descuentoSocio = +(subtotal * pct / 100).toFixed(2);
  return { subtotal, descuentoSocio, total: +(subtotal - descuentoSocio).toFixed(2) };
};
const carritoDto = uid => { const items = carritoDe(uid); return { usuarioId: uid, items, ...totales(uid, items) }; };
const FLUJO = { pagado: 'preparando', preparando: 'enviado', enviado: 'entregado' };

service('compras', 8084, r => {
  crud(r, '/productos', PR, 'productoId', {
    filters: (p, q) => (!q.categoria || p.categoria === q.categoria) && (!q.jugadorId || p.jugadorId === q.jugadorId) && (!q.patrocinadorId || p.patrocinadorId === q.patrocinadorId),
    make: (b, o = {}) => (!b.nombre || b.precio == null || b.precio < 0 || !b.categoria) ? 'nombre, precio (≥0) y categoria son obligatorios' : { productoId: uuid(), stock: [], fotos: [], ...o, ...b },
  });
  const prod = (pid) => PR.find(p => p.productoId === pid);
  r.get('/carrito', wrap((req, res) => res.json(carritoDto(me(req).id))));
  r.delete('/carrito', wrap((req, res) => { C.carritos.set(me(req).id, []); res.status(204).end(); }));
  r.post('/carrito/items', wrap((req, res) => {
    const u = me(req), { productoId, talla: t, cantidad } = req.body || {}, p = prod(productoId);
    if (!p) return err(res, 404, 'Producto no encontrado');
    if (!Number.isInteger(cantidad) || cantidad < 1) return err(res, 400, 'La cantidad debe ser un entero positivo');
    const s = p.stock.find(x => x.talla === t); if (!s) return err(res, 400, 'Talla no válida para este producto');
    const items = carritoDe(u.id), ex = items.find(i => i.productoId === productoId && i.talla === t);
    if ((ex?.cantidad || 0) + cantidad > s.unidades) return err(res, 409, 'No hay stock suficiente');
    if (ex) ex.cantidad += cantidad; else items.push({ itemId: uuid(), productoId, nombre: p.nombre, talla: t, cantidad, precioUnitario: p.precio });
    res.status(201).json(carritoDto(u.id));
  }));
  r.put('/carrito/items/:id', wrap((req, res) => {
    const u = me(req), it = carritoDe(u.id).find(i => i.itemId === req.params.id); if (!it) return err(res, 404, 'Elemento no encontrado');
    const c = req.body?.cantidad; if (!Number.isInteger(c) || c < 1) return err(res, 400, 'La cantidad debe ser un entero positivo');
    if (c > prod(it.productoId).stock.find(x => x.talla === it.talla).unidades) return err(res, 409, 'No hay stock suficiente');
    it.cantidad = c; res.json(carritoDto(u.id));
  }));
  r.delete('/carrito/items/:id', wrap((req, res) => {
    const u = me(req), items = carritoDe(u.id), i = items.findIndex(x => x.itemId === req.params.id); if (i < 0) return err(res, 404, 'Elemento no encontrado');
    items.splice(i, 1); res.status(204).end();
  }));
  r.post('/pedidos', wrap((req, res) => {
    const u = me(req), key = req.headers['idempotency-key'], { direccionEnvio, metodoPago } = req.body || {}, items = carritoDe(u.id);
    if (!key || key.length > 100) return err(res, 400, 'La cabecera Idempotency-Key es obligatoria (entre 1 y 100 caracteres)');
    if (!direccionEnvio || !['tarjeta', 'bizum', 'paypal'].includes(metodoPago)) return err(res, 400, 'direccionEnvio y metodoPago son obligatorios');
    const huella = `${direccionEnvio.trim()}|${metodoPago}`, previo = C.claves.get(`${u.id}:${key}`);
    if (previo) {                                                  // la misma clave devuelve el mismo pedido, sin cobrar de nuevo
      if (previo.huella !== huella) return err(res, 409, 'La Idempotency-Key ya se usó con otros datos');
      const p = C.pedidos.find(x => x.pedidoId === previo.pedidoId);
      if (p.estado === 'cancelado') return err(res, 409, 'El pedido de esta Idempotency-Key fue cancelado; usa una clave nueva');
      return res.status(201).json(p);
    }
    if (!items.length) return err(res, 409, 'El carrito está vacío');
    for (const i of items) if (i.cantidad > prod(i.productoId).stock.find(x => x.talla === i.talla).unidades) return err(res, 409, `No hay stock suficiente de ${i.nombre} (${i.talla})`);
    items.forEach(i => { prod(i.productoId).stock.find(x => x.talla === i.talla).unidades -= i.cantidad; });
    const t = totales(u.id, items);
    const pedido = { pedidoId: uuid(), usuarioId: u.id, items: items.map(i => ({ ...i })), total: t.total, descuentoSocio: t.descuentoSocio, direccionEnvio: direccionEnvio.trim(), metodoPago, estado: 'pagado', fechaPedido: now() };
    C.pedidos.push(pedido); C.carritos.set(u.id, []); C.claves.set(`${u.id}:${key}`, { huella, pedidoId: pedido.pedidoId });
    registrarActividad(u.id, 'pedido', pedido.pedidoId, pedido.total, `Pedido de ${pedido.items.reduce((n, i) => n + i.cantidad, 0)} artículo(s)`);       // evento "pedido creado"
    res.status(201).json(pedido);
  }));
  r.get('/pedidos', wrap((req, res) => {
    const u = me(req), q = req.query;
    if (!isStaff(u) && q.usuarioId && q.usuarioId !== u.id) return err(res, 403, 'Solo puedes ver tus pedidos');
    res.json(C.pedidos.filter(p => (isStaff(u) ? (!q.usuarioId || p.usuarioId === q.usuarioId) : p.usuarioId === u.id) && (!q.estado || p.estado === q.estado)).sort((a, b) => b.fechaPedido.localeCompare(a.fechaPedido)));
  }));
  const pedido = (req, res) => { const p = C.pedidos.find(x => x.pedidoId === req.params.id); if (!p) return err(res, 404, 'Pedido no encontrado'), null; const u = me(req); if (p.usuarioId !== u.id && !isStaff(u)) return err(res, 403, 'No es tu pedido'), null; return p; };
  r.get('/pedidos/:id', wrap((req, res) => { const p = pedido(req, res); p && res.json(p); }));
  r.delete('/pedidos/:id', wrap((req, res) => {
    const p = pedido(req, res); if (!p) return;
    if (['enviado', 'entregado'].includes(p.estado)) return err(res, 409, 'El pedido ya se ha enviado');
    if (p.estado !== 'cancelado') { p.estado = 'cancelado'; p.items.forEach(i => { prod(i.productoId).stock.find(x => x.talla === i.talla).unidades += i.cantidad; }); }
    res.status(204).end();
  }));
  r.put('/pedidos/:id/estado', wrap((req, res) => {
    if (!isStaff(me(req))) return err(res, 403, 'Solo staff o admin');
    const p = C.pedidos.find(x => x.pedidoId === req.params.id); if (!p) return err(res, 404, 'Pedido no encontrado');
    const e = req.body?.estado; if (e === p.estado) return res.json(p);
    if (FLUJO[p.estado] !== e) return err(res, 409, `Transición no permitida: ${p.estado} → ${e}`);
    p.estado = e; res.json(p);
  }));
});

// ═════════════ ESTADÍSTICAS ═════════════
const S = new Map();                      // partidoId → { estadoSeguimiento, periodo, segundos, version, jug:{id:contadores}, eventos:[] }
const zero = () => ({ titular: false, seg: 0, t2a: 0, t2i: 0, t3a: 0, t3i: 0, tla: 0, tli: 0, ro: 0, rd: 0, as: 0, rb: 0, pe: 0, tp: 0, tpr: 0, fc: 0, fr: 0, mm: 0 });
let seed = 7; const rnd = n => { seed = (seed * 48271) % 2147483647; return seed % (n + 1); };
const jugadoresActivos = J.filter(j => j.activo);
for (const p of P.filter(p => ['finalizado', 'en_juego'].includes(p.estado))) {
  const live = p.estado === 'en_juego', jug = {};
  jugadoresActivos.forEach((j, i) => {
    const c = zero(), k = live ? 0.5 : 1; c.titular = i < 5; c.seg = Math.round((c.titular ? 1500 + rnd(500) : 400 + rnd(500)) * k);
    c.t2i = Math.round((3 + rnd(6)) * k); c.t2a = Math.min(c.t2i, Math.round(c.t2i * (0.4 + rnd(30) / 100)));
    c.t3i = Math.round((1 + rnd(5)) * k); c.t3a = Math.min(c.t3i, Math.round(c.t3i * (0.25 + rnd(25) / 100)));
    c.tli = Math.round((1 + rnd(5)) * k); c.tla = Math.min(c.tli, Math.round(c.tli * 0.75));
    c.ro = rnd(2); c.rd = rnd(5); c.as = rnd(6); c.rb = rnd(3); c.pe = rnd(4); c.tp = rnd(2); c.tpr = rnd(2); c.fc = rnd(4); c.fr = rnd(4); c.mm = rnd(14) - 5; jug[j.jugadorId] = c;
  });
  S.set(p.partidoId, { estadoSeguimiento: live ? 'en_directo' : 'finalizado', periodo: live ? 3 : 4, segundos: live ? 312 : 0, version: 1, jug, eventos: [], clientIds: new Set() });
}
const pts = c => 2 * c.t2a + 3 * c.t3a + c.tla;
const pct = (a, i) => i ? +(100 * a / i).toFixed(1) : null;
const val = c => +(pts(c) + c.ro + c.rd + c.as + c.rb + c.tp + c.fr - (c.t2i - c.t2a) - (c.t3i - c.t3a) - (c.tli - c.tla) - c.pe - c.tpr - c.fc).toFixed(1);
const adv = c => { const fga = c.t2i + c.t3i, ts = 2 * (fga + 0.44 * c.tli); return { porcentajeTiroEfectivo: fga ? +(100 * (c.t2a + c.t3a + 0.5 * c.t3a) / fga).toFixed(1) : null, trueShooting: ts ? +(100 * pts(c) / ts).toFixed(1) : null, ratioAsistenciasPerdidas: c.pe ? +(c.as / c.pe).toFixed(2) : null }; };
const jDto = (p, jid, c) => ({
  partidoId: p.partidoId, jugadorId: jid, temporada: p.temporada, competicion: p.competicion, jornada: p.jornada, fechaHora: p.fechaHora, titular: c.titular, segundosJugados: c.seg, puntos: pts(c),
  tiros2Anotados: c.t2a, tiros2Intentados: c.t2i, tiros3Anotados: c.t3a, tiros3Intentados: c.t3i, tirosLibresAnotados: c.tla, tirosLibresIntentados: c.tli, rebotesOfensivos: c.ro, rebotesDefensivos: c.rd,
  rebotesTotales: c.ro + c.rd, asistencias: c.as, robos: c.rb, perdidas: c.pe, tapones: c.tp, taponesRecibidos: c.tpr, faltasCometidas: c.fc, faltasRecibidas: c.fr, masMenos: c.mm, valoracion: val(c),
  tirosCampoAnotados: c.t2a + c.t3a, tirosCampoIntentados: c.t2i + c.t3i, porcentajes: { tiros2: pct(c.t2a, c.t2i), tiros3: pct(c.t3a, c.t3i), tirosLibres: pct(c.tla, c.tli), tirosCampo: pct(c.t2a + c.t3a, c.t2i + c.t3i) }, metricasAvanzadas: adv(c),
});
const partidoDto = pid => {
  const p = P.find(x => x.partidoId === pid), s = S.get(pid) || { estadoSeguimiento: 'no_iniciado', periodo: null, segundos: null, jug: {}, version: 0 };
  return { partidoId: pid, temporada: p.temporada, estadoSeguimiento: s.estadoSeguimiento, ...(s.periodo ? { periodoActual: s.periodo, segundosRestantesPeriodo: s.segundos } : {}), jugadores: Object.entries(s.jug).map(([jid, c]) => jDto(p, jid, c)), actualizadoEn: now() };
};
const temporada = (t, jid) => {
  const out = new Map();
  for (const [pid, s] of S) { const p = P.find(x => x.partidoId === pid); if (p.temporada !== t) continue;
    for (const [id2, c] of Object.entries(s.jug)) { if (jid && id2 !== jid) continue; const a = out.get(id2) || { jugadorId: id2, partidos: [], c: zero() }; a.partidos.push(jDto(p, id2, c)); for (const k of Object.keys(c)) if (typeof c[k] === 'number') a.c[k] += c[k]; a.c.titular += c.titular ? 1 : 0; out.set(id2, a); } }
  return [...out.values()].map(({ jugadorId, partidos, c }) => {
    const n = partidos.length, tot = { segundosJugados: c.seg, puntos: pts(c), tiros2Anotados: c.t2a, tiros2Intentados: c.t2i, tiros3Anotados: c.t3a, tiros3Intentados: c.t3i, tirosLibresAnotados: c.tla, tirosLibresIntentados: c.tli, rebotesOfensivos: c.ro, rebotesDefensivos: c.rd, rebotesTotales: c.ro + c.rd, asistencias: c.as, robos: c.rb, perdidas: c.pe, tapones: c.tp, taponesRecibidos: c.tpr, faltasCometidas: c.fc, faltasRecibidas: c.fr, masMenos: c.mm, valoracion: val(c) };
    const av = k => +(tot[k] / n).toFixed(1);
    return { jugadorId, temporada: t, competicion: COMP, partidosJugados: n, titularidades: c.titular, totales: tot,
      promedios: { minutosPorPartido: +(c.seg / 60 / n).toFixed(1), puntosPorPartido: av('puntos'), rebotesPorPartido: av('rebotesTotales'), asistenciasPorPartido: av('asistencias'), robosPorPartido: av('robos'), perdidasPorPartido: av('perdidas'), taponesPorPartido: av('tapones'), faltasPorPartido: av('faltasCometidas'), masMenosPorPartido: av('masMenos'), valoracionPorPartido: av('valoracion') },
      porcentajes: { tiros2: pct(c.t2a, c.t2i), tiros3: pct(c.t3a, c.t3i), tirosLibres: pct(c.tla, c.tli), tirosCampo: pct(c.t2a + c.t3a, c.t2i + c.t3i) }, metricasAvanzadas: adv(c), _partidos: partidos };
  });
};
const metricaDe = (m, a) => { const [base, pp] = m.endsWith('_por_partido') ? [m.slice(0, -12), true] : [m, false];
  const tot = { puntos: a.totales.puntos, rebotes: a.totales.rebotesTotales, asistencias: a.totales.asistencias, robos: a.totales.robos, tapones: a.totales.tapones, valoracion: a.totales.valoracion }[base];
  if (tot !== undefined) return pp ? +(tot / a.partidosJugados).toFixed(1) : tot;
  return { porcentaje_tiros_2: a.porcentajes.tiros2, porcentaje_tiros_3: a.porcentajes.tiros3, porcentaje_tiros_libres: a.porcentajes.tirosLibres, porcentaje_tiros_campo: a.porcentajes.tirosCampo, porcentaje_tiro_efectivo: a.metricasAvanzadas.porcentajeTiroEfectivo, true_shooting: a.metricasAvanzadas.trueShooting, mas_menos: a.totales.masMenos }[m]; };
const METRICAS = ['puntos', 'puntos_por_partido', 'rebotes', 'rebotes_por_partido', 'asistencias', 'asistencias_por_partido', 'robos', 'robos_por_partido', 'tapones', 'tapones_por_partido', 'valoracion', 'valoracion_por_partido', 'porcentaje_tiros_2', 'porcentaje_tiros_3', 'porcentaje_tiros_libres', 'porcentaje_tiros_campo', 'porcentaje_tiro_efectivo', 'true_shooting', 'mas_menos'];
let broadcast = () => {};
const EFECTO = { tiro_2_anotado: c => { c.t2a++; c.t2i++; }, tiro_2_fallado: c => { c.t2i++; }, tiro_3_anotado: c => { c.t3a++; c.t3i++; }, tiro_3_fallado: c => { c.t3i++; }, tiro_libre_anotado: c => { c.tla++; c.tli++; }, tiro_libre_fallado: c => { c.tli++; },
  rebote_ofensivo: c => c.ro++, rebote_defensivo: c => c.rd++, asistencia: c => c.as++, robo: c => c.rb++, perdida: c => c.pe++, tapon: c => c.tp++, tapon_recibido: c => c.tpr++, falta_personal: c => c.fc++, falta_recibida: c => c.fr++ };

service('estadisticas', 8085, r => {
  const exists = (req, res) => P.some(p => p.partidoId === req.params.pid) || (err(res, 404, 'Recurso no encontrado en Deportivo'), false);
  r.get('/partidos/:pid/estadisticas', wrap((req, res) => exists(req, res) && res.json(partidoDto(req.params.pid))));
  r.put('/partidos/:pid/estadisticas', wrap((req, res) => {
    if (!exists(req, res)) return; const s = S.get(req.params.pid); if (!s) return err(res, 404, 'El partido no tiene seguimiento todavía');
    for (const { jugadorId, estadisticas: e } of req.body?.jugadores || []) { const c = s.jug[jugadorId]; if (!c) continue; if (e.titular !== undefined) c.titular = e.titular; if (e.segundosJugados !== undefined) c.seg = e.segundosJugados; if (e.masMenos !== undefined) c.mm = e.masMenos; }
    s.version++; broadcast(req.params.pid); res.json(partidoDto(req.params.pid));
  }));
  r.get('/partidos/:pid/jugadores/:jid/estadisticas', wrap((req, res) => { if (!exists(req, res)) return; const c = S.get(req.params.pid)?.jug[req.params.jid]; c ? res.json(jDto(P.find(p => p.partidoId === req.params.pid), req.params.jid, c)) : err(res, 404, 'Sin estadísticas para ese jugador'); }));
  r.put('/partidos/:pid/jugadores/:jid/estadisticas', wrap((req, res) => { const c = S.get(req.params.pid)?.jug[req.params.jid]; if (!c) return err(res, 404, 'Sin estadísticas para ese jugador'); Object.assign(c, { ...(req.body.segundosJugados !== undefined && { seg: req.body.segundosJugados }), ...(req.body.masMenos !== undefined && { mm: req.body.masMenos }) }); S.get(req.params.pid).version++; broadcast(req.params.pid); res.json(jDto(P.find(p => p.partidoId === req.params.pid), req.params.jid, c)); }));
  r.get('/partidos/:pid/eventos', wrap((req, res) => exists(req, res) && res.json((S.get(req.params.pid)?.eventos || []).filter(e => (!req.query.jugadorId || e.jugadorId === req.query.jugadorId) && (!req.query.tipo || e.tipo === req.query.tipo)))));
  r.post('/partidos/:pid/eventos', wrap((req, res) => {
    if (!exists(req, res)) return; const b = req.body || {};
    if (!b.clientEventoId || !b.tipo || !b.periodo) return err(res, 400, 'clientEventoId, tipo y periodo son obligatorios');
    if (!Object.keys(EFECTO).concat(['inicio_partido', 'inicio_periodo', 'fin_periodo', 'fin_partido', 'entra_pista', 'sale_pista']).includes(b.tipo)) return err(res, 400, 'Tipo de evento no válido');
    let s = S.get(req.params.pid); if (!s) { s = { estadoSeguimiento: 'no_iniciado', periodo: 1, segundos: 600, version: 0, jug: Object.fromEntries(jugadoresActivos.map(j => [j.jugadorId, zero()])), eventos: [], clientIds: new Set() }; S.set(req.params.pid, s); }
    if (s.clientIds.has(b.clientEventoId)) return err(res, 409, 'clientEventoId duplicado');
    const c = s.jug[b.jugadorId];
    if (EFECTO[b.tipo]) { if (!c) return err(res, 400, 'jugadorId obligatorio y válido en eventos individuales'); EFECTO[b.tipo](c); }
    if (b.tipo === 'inicio_partido') s.estadoSeguimiento = 'en_directo'; if (b.tipo === 'fin_periodo') s.estadoSeguimiento = 'descanso'; if (b.tipo === 'inicio_periodo') s.estadoSeguimiento = 'en_directo'; if (b.tipo === 'fin_partido') s.estadoSeguimiento = 'finalizado';
    s.periodo = b.periodo; if (b.segundosRestantes !== undefined) s.segundos = b.segundosRestantes;
    const ev = { ...b, eventoId: uuid(), partidoId: req.params.pid, registradoEn: now(), anulado: false }; s.eventos.push(ev); s.clientIds.add(b.clientEventoId); s.version++; broadcast(req.params.pid); res.status(201).json(ev);
  }));
  r.delete('/partidos/:pid/eventos/:eid', wrap((req, res) => {
    const s = S.get(req.params.pid), ev = s?.eventos.find(e => e.eventoId === req.params.eid); if (!ev) return err(res, 404, 'Evento no encontrado');
    if (!ev.anulado) { ev.anulado = true; ev.anuladoEn = now(); const c = s.jug[ev.jugadorId]; if (c && EFECTO[ev.tipo]) { const d = zero(); EFECTO[ev.tipo](d); for (const k in d) if (typeof d[k] === 'number') c[k] -= d[k]; } s.version++; broadcast(req.params.pid); }
    res.status(204).end();
  }));
  r.get('/jugadores/:jid/estadisticas', wrap((req, res) => {
    if (!/^\d{4}-\d{4}$/.test(req.query.temporada || '')) return err(res, 400, 'temporada (AAAA-AAAA) es obligatoria');
    const a = temporada(req.query.temporada, req.params.jid)[0]; if (!a) return err(res, 404, 'Sin estadísticas de ese jugador en la temporada');
    const { _partidos, ...dto } = a; res.json(req.query.incluirPartidos === 'true' ? { ...dto, detallePartidos: _partidos } : dto);
  }));
  r.get('/jugadores/:jid/partidos', wrap((req, res) => { const a = temporada(req.query.temporada || '', req.params.jid)[0]; a ? res.json(a._partidos) : err(res, 404, 'Sin estadísticas de ese jugador en la temporada'); }));
  r.get('/temporadas/:t/estadisticas', wrap((req, res) => res.json(temporada(req.params.t).map(({ _partidos, ...x }) => x))));
  r.get('/temporadas/:t/lideres', wrap((req, res) => {
    const m = req.query.metrica; if (!METRICAS.includes(m)) return err(res, 400, 'metrica no válida');
    const min = +(req.query.minimoPartidos ?? 1), lim = +(req.query.limite ?? 10);
    const jugadores = temporada(req.params.t).filter(a => a.partidosJugados >= min).map(a => ({ jugadorId: a.jugadorId, valor: metricaDe(m, a), partidosJugados: a.partidosJugados })).filter(x => x.valor != null)
      .sort((a, b) => b.valor - a.valor).slice(0, lim).map((x, i) => ({ posicion: i + 1, ...x }));
    res.json({ temporada: req.params.t, competicion: req.query.competicion, metrica: m, jugadores });
  }));
}, server => {
  const wss = new WebSocketServer({ server, path: '/v1/ws' }); const subs = new Map();
  const snap = (ws, pid) => ws.send(JSON.stringify({ tipo: 'estado', partidoId: pid, version: S.get(pid)?.version ?? 0, datos: partidoDto(pid) }));
  broadcast = pid => wss.clients.forEach(ws => subs.get(ws)?.has(pid) && ws.readyState === 1 && snap(ws, pid));
  wss.on('connection', ws => {
    subs.set(ws, new Set());
    ws.on('message', raw => {
      let m; try { m = JSON.parse(raw); } catch { return ws.send(JSON.stringify({ tipo: 'error', codigo: 'mensaje_invalido', mensaje: 'JSON no válido' })); }
      if (m.tipo === 'pong') return;
      if (m.tipo === 'cancelar_suscripcion') return subs.get(ws).delete(m.partidoId);
      if (m.tipo !== 'suscribir') return ws.send(JSON.stringify({ tipo: 'error', codigo: 'mensaje_invalido', mensaje: 'Usa suscribir, cancelar_suscripcion o pong' }));
      if (!P.some(p => p.partidoId === m.partidoId)) return ws.send(JSON.stringify({ tipo: 'error', codigo: 'partido_no_encontrado', mensaje: 'No existe el partido solicitado' }));
      subs.get(ws).add(m.partidoId); snap(ws, m.partidoId);
    });
    ws.on('close', () => subs.delete(ws));
  });
  setInterval(() => wss.clients.forEach(ws => ws.readyState === 1 && ws.send('{"tipo":"heartbeat"}')), 30_000).unref();
});

// ═════════════ INSTITUCIONAL ═════════════
const I = {
  historia: { texto: 'El Club Baloncesto Madroñera nació en 1985 con una idea sencilla: acercar el baloncesto a todas las edades del pueblo. Lo que empezó con dos canastas en el patio del colegio es hoy una entidad con equipos desde premini hasta el primer equipo.\nA lo largo de estas décadas el club ha crecido de la mano de sus socios, sus familias y de los vecinos que han llenado el pabellón cada fin de semana. Hoy seguimos fieles al mismo espíritu: formar jugadores y, sobre todo, personas.', fechaFundacion: '1985-06-01', hitos: [{ anio: 1985, descripcion: 'Fundación del club' }, { anio: 1992, descripcion: 'Primera escuela de baloncesto para niños y niñas' }, { anio: 1998, descripcion: 'Primer ascenso de categoría' }, { anio: 2005, descripcion: 'Inauguración del pabellón municipal' }, { anio: 2016, descripcion: 'Ascenso de categoría tras una temporada histórica' }, { anio: 2019, descripcion: 'Campeones de la liga regional' }, { anio: 2023, descripcion: 'Copa Extremadura' }] },
  identidad: { nombreOficial: 'CB Madroñera', escudoUrl: null, coloresOficiales: ['#0B5FA5', '#FFFFFF'] },
  junta: [['Ana García', 'Presidenta'], ['Manuel Rubio', 'Vicepresidente'], ['Lucía Herrera', 'Secretaria'], ['Marta Ruiz', 'Tesorera']].map(([nombre, cargo]) => ({ nombre, cargo, fotoUrl: null })),
  instalaciones: [{ instalacionId: id('80000000', 1), nombre: PAB, direccion: 'Calle del Deporte s/n, Madroñera', comoLlegar: 'Se accede desde la carretera principal; hay aparcamiento gratuito junto al pabellón y acceso adaptado para personas con movilidad reducida.', capacidad: 600, fotos: ['/img/club/pabellon.svg'], latitud: null, longitud: null }, { instalacionId: id('80000000', 2), nombre: 'Pista exterior', direccion: 'Calle del Deporte s/n, Madroñera', comoLlegar: 'Junto al pabellón. De uso libre cuando no hay entrenamientos de la cantera.', capacidad: null, fotos: ['/img/club/pista-exterior.svg'], latitud: null, longitud: null }],
  palmares: [['Ascenso de categoría', '2015-2016', 'Ascenso conseguido en la última jornada ante un pabellón lleno'], ['Campeón de liga regional', '2018-2019', 'Liga ganada con el mejor balance de victorias de la historia del club'], ['Copa Extremadura', '2022-2023', 'Final ganada en la prórroga']].map(([competicion, temporada, descripcion], i) => ({ tituloId: id('60000000', i + 1), competicion, temporada, descripcion, fotoUrl: null })),
  cantera: [['Premini', '8-9 años', 'Lucía Herrera', 'Lunes y miércoles 17:00'], ['Mini', '10-11 años', 'Manuel Rubio', 'Lunes y miércoles 18:00'], ['Infantil', '12-13 años', 'Ana García', 'Martes y jueves 17:00'], ['Cadete', '14-15 años', 'Marta Ruiz', 'Martes y jueves 18:00'], ['Junior', '16-17 años', 'Marta Ruiz', 'Martes y jueves 19:30']].map(([nombre, edades, entrenador, horarios], i) => ({ categoriaId: id('70000000', i + 1), nombre, edades, entrenador: entrenador ?? null, horarios: horarios ?? null })),
  patrocinadores: [['Banco Rural', 'principal', 'https://www.extremadura.com', 'banco-rural'], ['Ferretería López', 'oro', 'https://www.turismoextremadura.com', 'ferreteria-lopez'], ['Bar El Pabellón', 'plata', null, 'bar-el-pabellon'], ['Talleres Ruiz', 'colaborador', null, 'talleres-ruiz']].map(([nombre, nivel, enlace, logo], i) => ({ patrocinadorId: id('50000000', i + 1), nombre, logoUrl: `/img/patrocinadores/${logo}.svg`, enlace, nivel })),
};
service('institucional', 8086, r => {
  r.get('/club/historia', (_q, res) => res.json(I.historia)); r.put('/club/historia', wrap((req, res) => req.body?.texto ? res.json(I.historia = { hitos: [], ...req.body }) : err(res, 400, 'texto es obligatorio')));
  r.get('/club/identidad', (_q, res) => res.json(I.identidad)); r.put('/club/identidad', wrap((req, res) => req.body?.nombreOficial ? res.json(I.identidad = req.body) : err(res, 400, 'nombreOficial es obligatorio')));
  r.get('/club/junta', (_q, res) => res.json(I.junta)); r.put('/club/junta', wrap((req, res) => Array.isArray(req.body) ? res.json(I.junta = req.body) : err(res, 400, 'Se espera una lista')));
  r.get('/club/instalaciones', (_q, res) => res.json(I.instalaciones)); r.get('/club/cantera', (_q, res) => res.json(I.cantera));
  r.post('/club/instalaciones', wrap((req, res) => { const e = { instalacionId: uuid(), fotos: [], ...req.body }; I.instalaciones.push(e); res.status(201).json(e); }));
  r.post('/club/cantera', wrap((req, res) => { const e = { categoriaId: uuid(), ...req.body }; I.cantera.push(e); res.status(201).json(e); }));
  crud(r, '/club/palmares', I.palmares, 'tituloId', { make: (b, o = {}) => (!b.competicion || !b.temporada) ? 'competicion y temporada son obligatorias' : { tituloId: uuid(), descripcion: null, fotoUrl: null, ...o, ...b } });
  crud(r, '/patrocinadores', I.patrocinadores, 'patrocinadorId', { filters: (p, q) => !q.nivel || p.nivel === q.nivel, make: (b, o = {}) => (!b.nombre || !b.nivel) ? 'nombre y nivel son obligatorios' : { patrocinadorId: uuid(), logoUrl: null, enlace: null, ...o, ...b } });
});

console.log('\nMock listo. Usuarios de prueba (clave Club2026!): admin@club.test · staff@club.test · socio@club.test · visitante@club.test\n');
