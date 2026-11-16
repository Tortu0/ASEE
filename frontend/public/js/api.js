// Cliente de las seis APIs a través del gateway: /{servicio}/v1/**. Normaliza los errores.
export const cfg = await fetch('/config.json').then(r => r.json()).catch(() => ({ gateway: '', wsUrl: 'ws://localhost:8085/v1/ws' }));

export class ApiError extends Error {
  constructor(status, mensaje, detalles) { super(mensaje); this.status = status; this.detalles = detalles || []; }
}
const listeners = new Set();
export const session = {
  token: sessionStorage.getItem('token'), user: JSON.parse(sessionStorage.getItem('user') || 'null'),
  set(token, user) { this.token = token; this.user = user; token ? (sessionStorage.setItem('token', token), sessionStorage.setItem('user', JSON.stringify(user))) : (sessionStorage.removeItem('token'), sessionStorage.removeItem('user')); listeners.forEach(f => f()); },
  onChange: f => listeners.add(f),
  get staff() { return ['staff', 'admin'].includes(this.user?.rol); }, get admin() { return this.user?.rol === 'admin'; },
};

export async function call(svc, method, path, { body, query, headers } = {}) {
  const qs = query ? '?' + new URLSearchParams(Object.entries(query).filter(([, v]) => v !== undefined && v !== null && v !== '')) : '';
  let res;
  try {
    res = await fetch(`${cfg.gateway}/${svc}/v1${path}${qs}`, { method, headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...headers, ...(session.token ? { Authorization: `Bearer ${session.token}` } : {}) }, body: body ? JSON.stringify(body) : undefined });
  } catch { throw new ApiError(0, 'No se puede conectar con el gateway. ¿Está arrancado?'); }
  if (res.status === 204) return null;
  const text = await res.text(); let data = null; try { data = text ? JSON.parse(text) : null; } catch { /* no JSON */ }
  if (!res.ok) {
    if (res.status === 401 && session.token) { session.set(null, null); }
    throw new ApiError(res.status, data?.mensaje || data?.error || data?.message || `Error ${res.status} en ${svc}`, data?.detalles);
  }
  return data;
}
const g = (s, p, query) => call(s, 'GET', p, { query }), post = (s, p, body) => call(s, 'POST', p, { body }), put = (s, p, body) => call(s, 'PUT', p, { body }), del = (s, p) => call(s, 'DELETE', p);

export const api = {
  identidad: () => g('institucional', '/club/identidad'), historia: () => g('institucional', '/club/historia'), junta: () => g('institucional', '/club/junta'),
  instalaciones: () => g('institucional', '/club/instalaciones'), palmares: () => g('institucional', '/club/palmares'), cantera: () => g('institucional', '/club/cantera'),
  patrocinadores: () => g('institucional', '/patrocinadores'),
  partidos: q => g('deportivo', '/partidos', { tamano: 100, ...q }), partido: id => g('deportivo', `/partidos/${id}`),
  jugadores: q => g('deportivo', '/jugadores', { tamano: 100, ...q }), jugador: id => g('deportivo', `/jugadores/${id}`),
  noticias: q => g('deportivo', '/noticias', q), noticia: id => g('deportivo', `/noticias/${id}`),
  prefs: uid => g('deportivo', `/usuarios/${uid}/preferencias-notificacion`), guardarPrefs: (uid, b) => put('deportivo', `/usuarios/${uid}/preferencias-notificacion`, b),
  login: b => post('usuarios', '/auth/login', b), registrar: b => post('usuarios', '/usuarios', b),
  usuario: id => g('usuarios', `/usuarios/${id}`), editarUsuario: (id, b) => put('usuarios', `/usuarios/${id}`, b), usuarios: q => g('usuarios', '/usuarios', { tamano: 100, ...q }),
  cambiarRol: (id, rol) => put('usuarios', `/usuarios/${id}/rol`, { rol }), socio: id => g('usuarios', `/usuarios/${id}/socio`), altaSocio: (id, b) => put('usuarios', `/usuarios/${id}/socio`, b), historial: id => g('usuarios', `/usuarios/${id}/historial`),
  aforo: id => g('entradas', `/partidos/${id}/aforo`), precios: id => g('entradas', `/partidos/${id}/precios`), asientos: id => g('entradas', `/partidos/${id}/asientos`),
  reservar: b => post('entradas', '/reservas', b), cancelarReserva: id => del('entradas', `/reservas/${id}`), comprar: b => post('entradas', '/entradas', b),
  misEntradas: uid => g('entradas', '/entradas', { usuarioId: uid }), validar: id => post('entradas', `/entradas/${id}/validacion`), anularEntrada: id => del('entradas', `/entradas/${id}`),
  productos: q => g('compras', '/productos', { tamano: 100, ...q }), carrito: () => g('compras', '/carrito'), vaciar: () => del('compras', '/carrito'),
  añadir: b => post('compras', '/carrito/items', b), cantidad: (id, cantidad) => put('compras', `/carrito/items/${id}`, { cantidad }), quitar: id => del('compras', `/carrito/items/${id}`),
  pedir: (b, clave) => call('compras', 'POST', '/pedidos', { body: b, headers: { 'Idempotency-Key': clave } }), pedidos: q => g('compras', '/pedidos', q), cancelarPedido: id => del('compras', `/pedidos/${id}`), estadoPedido: (id, estado) => put('compras', `/pedidos/${id}/estado`, { estado }),
  statsPartido: id => g('estadisticas', `/partidos/${id}/estadisticas`), eventos: id => g('estadisticas', `/partidos/${id}/eventos`), registrarEvento: (id, b) => post('estadisticas', `/partidos/${id}/eventos`, b),
  anularEvento: (id, e) => del('estadisticas', `/partidos/${id}/eventos/${e}`),
  statsJugador: (id, temporada) => g('estadisticas', `/jugadores/${id}/estadisticas`, { temporada }), historicoJugador: (id, temporada) => g('estadisticas', `/jugadores/${id}/partidos`, { temporada }),
  statsTemporada: t => g('estadisticas', `/temporadas/${t}/estadisticas`), lideres: (t, metrica, limite = 10) => g('estadisticas', `/temporadas/${t}/lideres`, { metrica, limite, minimoPartidos: 1 }),
  salud: () => fetch(`${cfg.gateway}/_gateway/health`).then(r => r.json()),
  crud: (svc, path) => ({ list: q => g(svc, path, { tamano: 100, ...q }), crear: b => post(svc, path, b), editar: (id, b) => put(svc, `${path}/${id}`, b), borrar: id => del(svc, `${path}/${id}`) }),
};
export const TEMPORADA_DEFECTO = '2026-2027';
