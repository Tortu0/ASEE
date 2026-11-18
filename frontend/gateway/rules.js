// Reglas de acceso por operación, derivadas de los ficheros OpenAPI (Endpoints corregidos/).
// Niveles:  public → sin token · auth → cualquier usuario con JWT válido
//           staff  → staff o admin · admin → solo admin
// Los servicios aplican además sus comprobaciones de propietario (memoria §3.1); el gateway
// solo exige lo que el contrato marca como "solo staff/admin".
export const SERVICES = {
  usuarios:      { port: 8081 },
  deportivo:     { port: 8082 },
  entradas:      { port: 8083 },
  compras:       { port: 8084 },
  estadisticas:  { port: 8085 },
  institucional: { port: 8086 },
};

const R = [
  // ── usuarios
  ['usuarios', 'POST', '/auth/login', 'public'],
  ['usuarios', 'POST', '/usuarios', 'public'],
  ['usuarios', 'GET', '/usuarios', 'admin'],
  ['usuarios', 'GET', '/usuarios/{id}', 'auth'],
  ['usuarios', 'PUT', '/usuarios/{id}', 'auth'],
  ['usuarios', 'DELETE', '/usuarios/{id}', 'admin'],
  ['usuarios', 'PUT', '/usuarios/{id}/rol', 'admin'],
  ['usuarios', 'GET', '/usuarios/{id}/socio', 'auth'],
  ['usuarios', 'PUT', '/usuarios/{id}/socio', 'staff'],
  ['usuarios', 'GET', '/usuarios/{id}/historial', 'auth'],
  ['usuarios', 'POST', '/usuarios/{id}/historial', 'admin'],
  // ── deportivo
  ['deportivo', 'GET', '/partidos', 'public'],
  ['deportivo', 'POST', '/partidos', 'staff'],
  ['deportivo', 'GET', '/partidos/{id}', 'public'],
  ['deportivo', 'PUT', '/partidos/{id}', 'staff'],
  ['deportivo', 'DELETE', '/partidos/{id}', 'admin'],
  ['deportivo', 'GET', '/jugadores', 'public'],
  ['deportivo', 'POST', '/jugadores', 'staff'],
  ['deportivo', 'GET', '/jugadores/{id}', 'public'],
  ['deportivo', 'PUT', '/jugadores/{id}', 'staff'],
  ['deportivo', 'DELETE', '/jugadores/{id}', 'admin'],
  ['deportivo', 'GET', '/noticias', 'public'],
  ['deportivo', 'POST', '/noticias', 'staff'],
  ['deportivo', 'GET', '/noticias/{id}', 'public'],
  ['deportivo', 'PUT', '/noticias/{id}', 'staff'],
  ['deportivo', 'DELETE', '/noticias/{id}', 'staff'],
  ['deportivo', 'GET', '/usuarios/{id}/preferencias-notificacion', 'owner'],
  ['deportivo', 'PUT', '/usuarios/{id}/preferencias-notificacion', 'owner'],
  // ── entradas
  ['entradas', 'GET', '/partidos/{id}/asientos', 'public'],
  ['entradas', 'GET', '/partidos/{id}/aforo', 'public'],
  ['entradas', 'GET', '/partidos/{id}/precios', 'public'],
  ['entradas', 'POST', '/reservas', 'auth'],
  ['entradas', 'GET', '/reservas/{id}', 'auth'],
  ['entradas', 'DELETE', '/reservas/{id}', 'auth'],
  ['entradas', 'POST', '/entradas', 'auth'],
  ['entradas', 'GET', '/entradas', 'auth'],
  ['entradas', 'GET', '/entradas/{id}', 'auth'],
  ['entradas', 'DELETE', '/entradas/{id}', 'staff'],
  ['entradas', 'POST', '/entradas/{id}/validacion', 'staff'],
  // ── compras
  ['compras', 'GET', '/productos', 'public'],
  ['compras', 'POST', '/productos', 'staff'],
  ['compras', 'GET', '/productos/{id}', 'public'],
  ['compras', 'PUT', '/productos/{id}', 'staff'],
  ['compras', 'DELETE', '/productos/{id}', 'admin'],
  ['compras', 'GET', '/carrito', 'auth'],
  ['compras', 'DELETE', '/carrito', 'auth'],
  ['compras', 'POST', '/carrito/items', 'auth'],
  ['compras', 'PUT', '/carrito/items/{id}', 'auth'],
  ['compras', 'DELETE', '/carrito/items/{id}', 'auth'],
  ['compras', 'POST', '/pedidos', 'auth'],
  ['compras', 'GET', '/pedidos', 'auth'],
  ['compras', 'GET', '/pedidos/{id}', 'auth'],
  ['compras', 'DELETE', '/pedidos/{id}', 'auth'],
  ['compras', 'PUT', '/pedidos/{id}/estado', 'staff'],
  // ── estadisticas (lecturas públicas; escrituras staff/admin)
  ['estadisticas', 'GET', '/partidos/{id}/estadisticas', 'public'],
  ['estadisticas', 'PUT', '/partidos/{id}/estadisticas', 'staff'],
  ['estadisticas', 'GET', '/partidos/{id}/jugadores/{id}/estadisticas', 'public'],
  ['estadisticas', 'PUT', '/partidos/{id}/jugadores/{id}/estadisticas', 'staff'],
  ['estadisticas', 'GET', '/partidos/{id}/eventos', 'public'],
  ['estadisticas', 'POST', '/partidos/{id}/eventos', 'staff'],
  ['estadisticas', 'DELETE', '/partidos/{id}/eventos/{id}', 'staff'],
  ['estadisticas', 'GET', '/jugadores/{id}/estadisticas', 'public'],
  ['estadisticas', 'GET', '/jugadores/{id}/partidos', 'public'],
  ['estadisticas', 'GET', '/temporadas/{id}/estadisticas', 'public'],
  ['estadisticas', 'GET', '/temporadas/{id}/lideres', 'public'],
  // ── institucional
  ['institucional', 'GET', '/club/historia', 'public'],
  ['institucional', 'PUT', '/club/historia', 'staff'],
  ['institucional', 'GET', '/club/identidad', 'public'],
  ['institucional', 'PUT', '/club/identidad', 'admin'],
  ['institucional', 'GET', '/club/junta', 'public'],
  ['institucional', 'PUT', '/club/junta', 'admin'],
  ['institucional', 'GET', '/club/instalaciones', 'public'],
  ['institucional', 'POST', '/club/instalaciones', 'staff'],
  ['institucional', 'GET', '/club/palmares', 'public'],
  ['institucional', 'POST', '/club/palmares', 'staff'],
  ['institucional', 'PUT', '/club/palmares/{id}', 'staff'],
  ['institucional', 'DELETE', '/club/palmares/{id}', 'admin'],
  ['institucional', 'GET', '/club/cantera', 'public'],
  ['institucional', 'POST', '/club/cantera', 'staff'],
  ['institucional', 'GET', '/patrocinadores', 'public'],
  ['institucional', 'POST', '/patrocinadores', 'admin'],
  ['institucional', 'GET', '/patrocinadores/{id}', 'public'],
  ['institucional', 'PUT', '/patrocinadores/{id}', 'admin'],
  ['institucional', 'DELETE', '/patrocinadores/{id}', 'admin'],
];

export const RULES = R.map(([svc, method, path, level]) => ({
  svc, method, level,
  re: new RegExp('^' + path.replace(/\{id\}/g, '([^/]+)') + '/?$'),
}));

export const RANK = { public: 0, auth: 1, staff: 2, admin: 3 };
export const ROLE_RANK = { visitante: 1, socio: 1, staff: 2, admin: 3 };
