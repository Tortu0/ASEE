#!/usr/bin/env node
// Verifica el backend REAL (o el mock) contra el contrato y comprueba las comunicaciones entre microservicios.
//   npm run verify                       (gateway en http://localhost:8080)
// Variables:  GATEWAY, STAFF_EMAIL/STAFF_PASSWORD, ADMIN_EMAIL/ADMIN_PASSWORD, PARTIDO_ENTRADAS, DIRECT_HOST
// Sale con código 1 si algún control falla. No borra datos, pero SÍ crea un usuario, una reserva/entradas y un pedido de prueba.
const GW = process.env.GATEWAY || 'http://localhost:8080';
const HOST = process.env.DIRECT_HOST || 'localhost';
const STAFF = { email: process.env.STAFF_EMAIL || 'staff@club.test', password: process.env.STAFF_PASSWORD || 'Club2026!' };
const ADMIN = { email: process.env.ADMIN_EMAIL || 'admin@club.test', password: process.env.ADMIN_PASSWORD || 'Club2026!' };
const PARTIDO = process.env.PARTIDO_ENTRADAS || '20000000-0000-0000-0000-000000000004';
const PUERTOS = { usuarios: 8081, deportivo: 8082, entradas: 8083, compras: 8084, estadisticas: 8085, institucional: 8086 };
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const res = []; let seccion = '';
const sec = t => { seccion = t; console.log(`\n\x1b[1m${t}\x1b[0m`); };
class Skip extends Error {}
class Warn extends Error {}
const skip = m => { throw new Skip(m); };
const warn = m => { throw new Warn(m); };
async function t(nombre, fn, pista) {
  try { const d = await fn(); res.push({ seccion, nombre, r: 'PASS' }); console.log(`  \x1b[32mPASS\x1b[0m ${nombre}${d ? `  \x1b[2m${d}\x1b[0m` : ''}`); }
  catch (e) {
    if (e instanceof Warn) { res.push({ seccion, nombre, r: 'WARN', motivo: e.message, pista }); console.log(`  \x1b[33mWARN\x1b[0m ${nombre}\n       ${e.message}${pista ? `\n       \x1b[36m→ ${pista}\x1b[0m` : ''}`); }
    else if (e instanceof Skip) { res.push({ seccion, nombre, r: 'SKIP' }); console.log(`  \x1b[33mSKIP\x1b[0m ${nombre}  \x1b[2m${e.message}\x1b[0m`); }
    else { res.push({ seccion, nombre, r: 'FAIL', motivo: e.message, pista }); console.log(`  \x1b[31mFAIL\x1b[0m ${nombre}\n       ${e.message}${pista ? `\n       \x1b[36m→ ${pista}\x1b[0m` : ''}`); }
  }
}
const eq = (a, b, m) => { if (a !== b) throw new Error(`${m}: esperado ${b}, recibido ${a}`); };
const has = (o, campos, ctx) => { const f = campos.filter(c => !(c in (o || {}))); if (f.length) throw new Error(`${ctx}: faltan campos ${f.join(', ')} (recibido: ${Object.keys(o || {}).join(', ') || 'nada'})`); };

async function http(base, method, path, { token, body, headers } = {}) {
  const r = await fetch(base + path, { method, headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers }, body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(8000) });
  const txt = await r.text(); let json = null; try { json = txt ? JSON.parse(txt) : null; } catch { /* no JSON */ }
  return { status: r.status, json, txt };
}
const gw = (svc, method, path, o) => http(`${GW}/${svc}/v1`, method, path, o);
const ok = (r, esperado, ctx) => { const e = [].concat(esperado); if (!e.includes(r.status)) throw new Error(`${ctx}: HTTP ${r.status} (esperado ${e.join('/')}) ${r.txt.slice(0, 160)}`); return r.json; };
const login = async c => (await gw('usuarios', 'POST', '/auth/login', { body: c })).json;
const decode = tk => JSON.parse(Buffer.from(tk.split('.')[1], 'base64url').toString());
const sleep = ms => new Promise(r => setTimeout(r, ms));

console.log(`Verificación del backend · gateway ${GW}`);

// ─────────────────────────────────────────────────────────────────────────
sec('1. Disponibilidad y contrato de rutas (puerto + prefijo /v1)');
for (const [svc, port] of Object.entries(PUERTOS)) {
  await t(`${svc} responde en :${port} con prefijo /v1`, async () => {
    const r = await http(`http://${HOST}:${port}`, 'GET', svc === 'usuarios' ? '/v1/auth/login' : '/v1/' + { deportivo: 'partidos?tamano=1', entradas: `partidos/${PARTIDO}/aforo`, compras: 'productos', institucional: 'club/identidad', estadisticas: 'temporadas/2026-2027/estadisticas' }[svc]).catch(e => { throw new Error(`sin conexión (${e.cause?.code || e.message})`); });
    if (svc === 'usuarios') { if (![405, 400, 200].includes(r.status)) throw new Error(`GET /v1/auth/login → HTTP ${r.status}`); } else if (r.status !== 200) throw new Error(`HTTP ${r.status}`);
    return `HTTP ${r.status}`;
  }, svc === 'usuarios' || svc === 'compras' ? 'Falta server.port y/o server.servlet.context-path=/v1 en application.yaml (el contrato fija puerto y /v1).' : 'Revisa puerto y context-path en application.yaml.');
}
await t('sin colisión de puertos: cada servicio responde con su propio contrato', async () => {
  const id = await http(`http://${HOST}:8082`, 'GET', '/v1/club/identidad'); if (id.status === 200) throw new Error('Deportivo responde con datos de Institucional: dos procesos compiten por el mismo puerto');
});

// ─────────────────────────────────────────────────────────────────────────
sec('2. Contrato de lectura pública (campos del OpenAPI)');
let partidos, jugadores, productos;
await t('Deportivo GET /partidos', async () => { partidos = ok(await gw('deportivo', 'GET', '/partidos'), 200, 'GET /partidos'); if (!partidos.length) throw new Error('lista vacía'); has(partidos[0], ['partidoId', 'fechaHora', 'rival', 'lugar', 'esLocal', 'temporada', 'estado', 'ventaEntradasAbierta'], 'Partido'); return `${partidos.length} partidos`; });
await t('Deportivo GET /jugadores', async () => { jugadores = ok(await gw('deportivo', 'GET', '/jugadores'), 200, 'GET /jugadores'); has(jugadores[0], ['jugadorId', 'nombre', 'dorsal', 'posicion', 'activo'], 'Jugador'); return `${jugadores.length} jugadores`; });
await t('Deportivo GET /noticias', async () => { const n = ok(await gw('deportivo', 'GET', '/noticias'), 200, 'GET /noticias'); if (n.length) has(n[0], ['noticiaId', 'titulo', 'contenido', 'categoria', 'fechaPublicacion'], 'Noticia'); });
await t('Institucional GET /club/identidad + /patrocinadores', async () => { has(ok(await gw('institucional', 'GET', '/club/identidad'), 200, 'identidad'), ['nombreOficial', 'coloresOficiales'], 'Identidad'); ok(await gw('institucional', 'GET', '/patrocinadores'), 200, 'patrocinadores'); });
await t('Entradas GET /partidos/{id}/aforo y /precios', async () => { has(ok(await gw('entradas', 'GET', `/partidos/${PARTIDO}/aforo`), 200, 'aforo'), ['capacidadTotal', 'ocupados', 'reservados', 'libres'], 'Aforo'); if (!ok(await gw('entradas', 'GET', `/partidos/${PARTIDO}/precios`), 200, 'precios').length) throw new Error('sin tarifas'); });
await t('Compras GET /productos (contrato: productoId, stock[], categoria)', async () => {
  productos = ok(await gw('compras', 'GET', '/productos'), 200, 'GET /productos'); has(productos[0], ['productoId', 'nombre', 'categoria', 'precio', 'stock'], 'Producto');
  if (!Array.isArray(productos[0].stock)) throw new Error('stock debe ser una lista [{talla, unidades}]');
}, 'Compras expone /api/productos con id/stockPorTalla; el contrato pide /v1/productos con productoId/stock[].');
await t('Estadísticas GET /temporadas/{t}/estadisticas', async () => { ok(await gw('estadisticas', 'GET', `/temporadas/${partidos?.[0]?.temporada || '2026-2027'}/estadisticas`), 200, 'temporada'); });

// ─────────────────────────────────────────────────────────────────────────
sec('3. Identidad y autorización (Usuarios ⇄ Gateway ⇄ resto)');
const email = `verif.${Date.now()}@club.test`, pass = 'Verif#2026ok'; let tk, uid, staffTk, adminTk;
await (async () => { const l = await login(STAFF).catch(() => null); staffTk = l?.token; })();
await t('Usuarios: registro y login emiten JWT con sub=UUID y rol', async () => {
  ok(await gw('usuarios', 'POST', '/usuarios', { body: { nombre: 'Usuario Verificación', email, password: pass } }), 201, 'registro');
  const l = ok(await gw('usuarios', 'POST', '/auth/login', { body: { email, password: pass } }), 200, 'login'); has(l, ['token', 'tipo', 'expiraEn', 'usuario'], 'LoginResponse');
  tk = l.token; uid = l.usuario.usuarioId; const c = decode(tk); if (!UUID.test(c.sub)) throw new Error('sub no es UUID'); return `rol=${c.rol}, alg=${JSON.parse(Buffer.from(tk.split('.')[0], 'base64url')).alg}`;
});
await t('Gateway: sin token → 401; con rol insuficiente → 403', async () => {
  eq((await gw('deportivo', 'POST', '/noticias', { body: {} })).status, 401, 'sin token'); eq((await gw('deportivo', 'POST', '/noticias', { token: tk, body: {} })).status, 403, 'visitante');
});
await t('Gateway ignora X-User-Role/X-User-Id enviados por el cliente', async () => { eq((await gw('usuarios', 'GET', '/usuarios', { headers: { 'X-User-Role': 'admin', 'X-User-Id': uid } })).status, 401, 'cabeceras falsas'); });
await t('Servicios SIN token: Deportivo/Institucional/Compras rechazan escrituras directas', async () => {
  const exp = [];
  for (const [svc, path, body] of [['deportivo', '/v1/noticias', { titulo: 'x', contenido: 'x', categoria: 'club' }], ['institucional', '/v1/patrocinadores', { nombre: 'x', nivel: 'oro' }]]) {
    const r = await http(`http://${HOST}:${PUERTOS[svc]}`, 'POST', path, { body }); if ([200, 201].includes(r.status)) exp.push(`${svc} (HTTP ${r.status})`);
  } if (exp.length) warn(`escritura anónima aceptada directamente en: ${exp.join(', ')}. Hoy solo protege el gateway.`);
}, 'Si alguien alcanza el puerto del servicio salta toda la seguridad. Valida el JWT también en el servicio, o aísla la red (solo el gateway expuesto).');
await t('Usuarios exige X-User-Id/X-User-Role: ¿lo cubre el gateway?', async () => { const r = await gw('usuarios', 'GET', `/usuarios/${uid}`, { token: tk }); ok(r, 200, 'GET /usuarios/{id} vía gateway'); });
await t('Usuarios: GET /usuarios solo admin (403 al visitante, 200 y lista al admin)', async () => {
  eq((await gw('usuarios', 'GET', '/usuarios', { token: tk })).status, 403, 'visitante');
  const l = await login(ADMIN); if (!l?.token) skip('no hay credenciales admin (ADMIN_EMAIL/ADMIN_PASSWORD; Usuarios las crea al arrancar con esas mismas variables)'); adminTk = l.token;
  const us = ok(await gw('usuarios', 'GET', '/usuarios', { token: adminTk }), 200, 'listado'); if (!Array.isArray(us)) throw new Error('el listado debe ser una lista'); has(us[0], ['usuarioId', 'nombre', 'email', 'rol', 'fechaAlta'], 'Usuario');
  if (us.some(u => 'password' in u)) throw new Error('el listado expone el campo password');
}, 'Falta GET /usuarios en Usuarios, o no existe el admin inicial (variables ADMIN_EMAIL y ADMIN_PASSWORD al arrancar Usuarios).');
await t('Usuarios: staff puede dar de alta a un socio (PUT /usuarios/{id}/socio)', async () => {
  if (!staffTk) skip('sin token staff'); const r = await gw('usuarios', 'PUT', `/usuarios/${uid}/socio`, { token: staffTk, body: { tipoCuota: 'anual', fechaCaducidad: new Date(Date.now() + 365 * 864e5).toISOString().slice(0, 10) } });
  has(ok(r, 200, 'alta socio'), ['estado', 'descuentoPorcentaje'], 'Socio');
}, 'El contrato permite staff o admin; antes Usuarios solo aceptaba admin.');
await t('Usuarios: POST /usuarios/{id}/historial solo admin', async () => {
  if (!adminTk) skip('sin token admin'); const ref = `00000000-0000-4000-8000-${String(Date.now()).slice(-12).padStart(12, '0')}`;
  eq((await gw('usuarios', 'POST', `/usuarios/${uid}/historial`, { token: staffTk || tk, body: { tipo: 'entrada', referenciaId: ref, fecha: new Date().toISOString() } })).status, 403, 'no admin');
  const b = { tipo: 'pedido', referenciaId: ref, fecha: new Date().toISOString(), importe: 1.5, descripcion: 'Alta de verificación' };
  ok(await gw('usuarios', 'POST', `/usuarios/${uid}/historial`, { token: adminTk, body: b }), 201, 'alta'); eq((await gw('usuarios', 'POST', `/usuarios/${uid}/historial`, { token: adminTk, body: b })).status, 409, 'duplicada');
}, 'Falta POST /usuarios/{id}/historial en Usuarios.');
await t('Estadísticas acepta la identidad staff a través del gateway (intercambio RS256)', async () => {
  const l = await login(STAFF); if (!l?.token) skip(`no hay credenciales staff (define STAFF_EMAIL/STAFF_PASSWORD): ${JSON.stringify(l)?.slice(0, 80)}`); staffTk = l.token;
  const r = await gw('estadisticas', 'POST', `/partidos/${partidos[0].partidoId}/eventos`, { token: staffTk, body: { clientEventoId: `verif-${Date.now()}`, tipo: 'inicio_periodo', periodo: 1, segundosRestantes: 600 } });
  if (r.status === 401) throw new Error('Estadísticas devolvió 401: no acepta el token (¿JWT_PUBLIC_KEY=file:gateway/keys/public.pem, issuer asee-local y audience estadisticas?)'); if (![200, 201, 404, 409].includes(r.status)) throw new Error(`HTTP ${r.status} ${r.txt.slice(0, 120)}`);
}, 'Estadísticas valida RS256 + iss + aud; Usuarios firma HS256 sin iss/aud. El gateway reemite el token; configura Estadísticas con la clave pública que imprime al arrancar.');
await t('Entradas acepta el JWT de Usuarios (perfil dev o decodificador HS256)', async () => { const r = await gw('entradas', 'GET', '/entradas', { token: tk, query: 0 }).catch(() => null); const q = await gw('entradas', 'GET', `/entradas?usuarioId=${uid}`, { token: tk }); if (q.status === 401) throw new Error('401: Entradas no valida el token emitido por Usuarios (usa issuer-uri de Keycloak)'); ok(q, 200, 'GET /entradas'); },
  'Arranca Entradas con SPRING_PROFILES_ACTIVE=dev o configura un JwtDecoder HS256 con el mismo secreto que Usuarios.');

// ─────────────────────────────────────────────────────────────────────────
sec('4. Flujo de compra de entradas (Entradas → Deportivo / Usuarios → evento → Usuarios)');
let entradas;
await t('Entradas: reservar asientos de un partido con venta abierta (consulta a Deportivo)', async () => {
  const libres = ok(await gw('entradas', 'GET', `/partidos/${PARTIDO}/asientos`, { query: 0 }), 200, 'asientos').filter(a => a.estado === 'libre'); if (libres.length < 2) throw new Error('no hay asientos libres');
  const r = await gw('entradas', 'POST', '/reservas', { token: tk, body: { partidoId: PARTIDO, asientos: [libres[0].asientoId, libres[1].asientoId] } }); const rv = ok(r, 201, 'reserva'); has(rv, ['reservaId', 'asientos', 'expiraEn'], 'Reserva'); globalThis.__rv = rv; return `reserva ${rv.reservaId.slice(0, 8)}`;
}, 'Si devuelve 503/404: Entradas no alcanza Deportivo (DEPORTIVO_URL) o el partido no existe en Deportivo.');
await t('Entradas: pagar y emitir entradas con QR (consulta tarifa de socio a Usuarios)', async () => {
  if (!globalThis.__rv) skip('sin reserva'); const r = await gw('entradas', 'POST', '/entradas', { token: tk, body: { reservaId: globalThis.__rv.reservaId, tipoEntrada: 'general', metodoPago: 'tarjeta' } });
  entradas = ok(r, 201, 'compra'); has(entradas[0], ['entradaId', 'codigoQr', 'precioPagado', 'estado', 'asiento'], 'Entrada'); return `${entradas.length} entradas`;
}, 'HTTP 503 aquí = Entradas→Usuarios falla. Usuarios exige X-User-Id/X-User-Role y Entradas solo reenvía el Bearer: apunta USUARIOS_URL al gateway (http://localhost:8080/usuarios/v1) o añade las cabeceras en UsuariosClient.');
await t('Evento "entrada comprada" llega a Usuarios y aparece en el historial (RabbitMQ)', async () => {
  if (!entradas) skip('no se compraron entradas');
  for (let i = 0; i < 15; i++) { const h = ok(await gw('usuarios', 'GET', `/usuarios/${uid}/historial`, { token: tk }), 200, 'historial'); const a = h.find(x => x.tipo === 'entrada'); if (a) { has(a, ['tipo', 'referenciaId', 'fecha', 'importe', 'descripcion'], 'Actividad'); return `tras ~${i}s`; } await sleep(1000); }
  throw new Error('tras 15 s el historial sigue vacío: el evento no llega a Usuarios');
}, 'Causas probadas en el código: (1) la cola q.usuarios.actividades de Usuarios no está enlazada a ningún exchange; (2) el payload de Entradas no trae "tipo" y Usuarios lo exige; (3) credenciales RabbitMQ distintas (guest/guest, admin/admin, admin/admin123).');

// ─────────────────────────────────────────────────────────────────────────
sec('5. Flujo de tienda (Compras)');
let carrito, stockAntes, prodElegido, tallaElegida;
const clave = `verif-${Date.now()}`, direccion = { direccionEnvio: 'Calle Verificación 1', metodoPago: 'tarjeta' };
const unidades = async () => (ok(await gw('compras', 'GET', `/productos/${prodElegido.productoId}`), 200, 'producto').stock.find(x => x.talla === tallaElegida)?.unidades);
await t('Compras: añadir al carrito (contrato /carrito/items, requiere token)', async () => {
  prodElegido = productos?.find(x => x.stock?.some(s => s.unidades > 0)); if (!prodElegido) skip('sin producto con stock en el catálogo (créalo desde Gestión)');
  tallaElegida = prodElegido.stock.find(x => x.unidades > 0).talla; stockAntes = await unidades();
  carrito = ok(await gw('compras', 'POST', '/carrito/items', { token: tk, body: { productoId: prodElegido.productoId, talla: tallaElegida, cantidad: 1 } }), 201, 'carrito');
  has(carrito, ['items', 'subtotal', 'descuentoSocio', 'total'], 'Carrito'); eq(await unidades(), stockAntes, 'el carrito no debe reservar stock');
}, 'Compras debe seguir el contrato: POST /v1/carrito/items con identidad (Bearer o X-User-*). Si da 503, Compras no alcanza a Usuarios (USUARIOS_URL).');
await t('Compras: el carrito rechaza más unidades que el stock (409) y tallas inexistentes (400)', async () => {
  if (!carrito) skip('sin carrito');
  eq((await gw('compras', 'POST', '/carrito/items', { token: tk, body: { productoId: prodElegido.productoId, talla: tallaElegida, cantidad: stockAntes + 1 } })).status, 409, 'más que el stock');
  eq((await gw('compras', 'POST', '/carrito/items', { token: tk, body: { productoId: prodElegido.productoId, talla: 'TALLA-INEXISTENTE', cantidad: 1 } })).status, 400, 'talla inexistente');
});
await t('Compras: POST /pedidos exige la cabecera Idempotency-Key (400 sin ella)', async () => {
  if (!carrito) skip('sin carrito'); eq((await gw('compras', 'POST', '/pedidos', { token: tk, body: direccion })).status, 400, 'sin Idempotency-Key');
});
let pedido;
await t('Compras: crear pedido con Idempotency-Key → 201, stock descontado y carrito vacío', async () => {
  if (!carrito) skip('sin carrito'); pedido = ok(await gw('compras', 'POST', '/pedidos', { token: tk, body: direccion, headers: { 'Idempotency-Key': clave } }), 201, 'pedido');
  has(pedido, ['pedidoId', 'items', 'total', 'estado', 'fechaPedido'], 'Pedido'); if (!UUID.test(pedido.pedidoId)) throw new Error('pedidoId no es UUID');
  eq(pedido.estado, 'pagado', 'estado tras el pago'); eq(await unidades(), stockAntes - 1, 'stock tras el pedido');
  const c = ok(await gw('compras', 'GET', '/carrito', { token: tk }), 200, 'carrito'); eq(c.items.length, 0, 'carrito tras pagar');
});
await t('Compras: repetir la misma Idempotency-Key devuelve el MISMO pedido sin cobrar ni descontar de nuevo', async () => {
  if (!pedido) skip('sin pedido'); const r = ok(await gw('compras', 'POST', '/pedidos', { token: tk, body: direccion, headers: { 'Idempotency-Key': clave } }), 201, 'reintento');
  eq(r.pedidoId, pedido.pedidoId, 'mismo pedido'); eq(await unidades(), stockAntes - 1, 'el stock no debe descontarse otra vez');
}, 'POST /pedidos debe ser idempotente por (usuario, Idempotency-Key): memoria 3.6.');
await t('Compras: la misma clave con otros datos → 409', async () => {
  if (!pedido) skip('sin pedido'); eq((await gw('compras', 'POST', '/pedidos', { token: tk, body: { ...direccion, direccionEnvio: 'Otra dirección' }, headers: { 'Idempotency-Key': clave } })).status, 409, 'clave reutilizada');
});
await t('Compras: GET /pedidos y /pedidos/{id} del propietario; otro usuario → 403', async () => {
  if (!pedido) skip('sin pedido'); const lista = ok(await gw('compras', 'GET', '/pedidos', { token: tk }), 200, 'lista'); if (!lista.some(p => p.pedidoId === pedido.pedidoId)) throw new Error('el pedido no aparece en la lista');
  ok(await gw('compras', 'GET', `/pedidos/${pedido.pedidoId}`, { token: tk }), 200, 'detalle');
  const otro = await gw('usuarios', 'POST', '/usuarios', { body: { nombre: 'Otro', email: `otro.${Date.now()}@club.test`, password: pass } }); const lo = await login({ email: otro.json?.email, password: pass });
  if (!lo?.token) skip('no se pudo crear un segundo usuario'); eq((await gw('compras', 'GET', `/pedidos/${pedido.pedidoId}`, { token: lo.token })).status, 403, 'pedido ajeno');
});
await t('Evento "pedido creado" llega a Usuarios y aparece en el historial con importe', async () => {
  if (!pedido) skip('sin pedido');
  for (let i = 0; i < 15; i++) { const h = ok(await gw('usuarios', 'GET', `/usuarios/${uid}/historial`, { token: tk }), 200, 'historial'); const a = h.find(x => x.tipo === 'pedido'); if (a) { has(a, ['referenciaId', 'importe', 'descripcion'], 'Actividad'); eq(a.referenciaId, pedido.pedidoId, 'referenciaId'); return `tras ~${i}s`; } await sleep(1000); }
  throw new Error('tras 15 s no hay actividad de tipo "pedido"');
}, 'Compras publica en club.exchange (routing key pedido.creado) y Usuarios escucha q.usuarios.actividades. Revisa las credenciales RABBIT_USER/RABBIT_PASS y los bindings en la consola de RabbitMQ.');
await t('Compras: staff avanza el estado (pagado→preparando) y un cliente no puede (403)', async () => {
  if (!pedido) skip('sin pedido'); eq((await gw('compras', 'PUT', `/pedidos/${pedido.pedidoId}/estado`, { token: tk, body: { estado: 'preparando' } })).status, 403, 'cliente');
  if (!staffTk) skip('sin credenciales staff'); eq(ok(await gw('compras', 'PUT', `/pedidos/${pedido.pedidoId}/estado`, { token: staffTk, body: { estado: 'preparando' } }), 200, 'estado').estado, 'preparando', 'nuevo estado');
  eq((await gw('compras', 'PUT', `/pedidos/${pedido.pedidoId}/estado`, { token: staffTk, body: { estado: 'entregado' } })).status, 409, 'saltarse pasos');
});
await t('Compras: cancelar repone el stock una sola vez (DELETE 204, repetible)', async () => {
  if (!pedido) skip('sin pedido'); eq((await gw('compras', 'DELETE', `/pedidos/${pedido.pedidoId}`, { token: tk })).status, 204, 'cancelar');
  eq(await unidades(), stockAntes, 'stock repuesto'); eq((await gw('compras', 'DELETE', `/pedidos/${pedido.pedidoId}`, { token: tk })).status, 204, 'cancelar otra vez');
  eq(await unidades(), stockAntes, 'el stock no se repone dos veces');
});

// ─────────────────────────────────────────────────────────────────────────
sec('6. Estadísticas ⇄ Deportivo y tiempo real');
await t('Estadísticas valida el partido contra Deportivo (partido inexistente → 404)', async () => { eq((await gw('estadisticas', 'GET', '/partidos/99999999-9999-9999-9999-999999999999/estadisticas')).status, 404, 'partido inexistente'); });
await t('Estadísticas: GET /partidos/{id}/estadisticas con estructura del contrato', async () => { const fin = partidos.find(p => p.estado !== 'programado') || partidos[0]; const d = ok(await gw('estadisticas', 'GET', `/partidos/${fin.partidoId}/estadisticas`), 200, 'stats'); has(d, ['partidoId', 'estadoSeguimiento', 'jugadores'], 'EstadisticasPartido'); });
await t('WebSocket /v1/ws acepta suscripción y devuelve estado', async () => {
  const url = `ws://${HOST}:${PUERTOS.estadisticas}/v1/ws`; const { WebSocket } = await import('ws').catch(() => ({ WebSocket: globalThis.WebSocket })); const pid = partidos[0].partidoId;
  await new Promise((ok_, ko) => { const w = new WebSocket(url); const to = setTimeout(() => { w.close(); ko(new Error('sin respuesta en 5 s')); }, 5000); w.onopen = () => w.send(JSON.stringify({ tipo: 'suscribir', partidoId: pid })); w.onerror = e => { clearTimeout(to); ko(new Error('no conecta a ' + url)); };
    w.onmessage = m => { const d = JSON.parse(m.data.toString()); if (d.tipo === 'estado') { clearTimeout(to); w.close(); ok_(); } else if (d.tipo === 'error') { clearTimeout(to); w.close(); ko(new Error(d.mensaje)); } }; });
});

// ─────────────────────────────────────────────────────────────────────────
const n = k => res.filter(x => x.r === k).length;
console.log(`\n\x1b[1mResumen\x1b[0m  ${n('PASS')} correctos · ${n('FAIL')} fallos · ${n('WARN')} avisos · ${n('SKIP')} omitidos`);
if (n('FAIL')) { console.log('\nFallos:'); res.filter(x => x.r === 'FAIL').forEach(x => console.log(`  - [${x.seccion.split('.')[0]}] ${x.nombre}`)); }
process.exit(n('FAIL') ? 1 : 0);
