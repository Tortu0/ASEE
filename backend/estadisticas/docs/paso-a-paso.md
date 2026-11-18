# Qué se ha implementado, paso a paso

## 1. Mantener el contrato y la estructura del equipo

Se ha conservado sin modificar `docs/openapi/estadisticas.yaml`, tomado del ZIP de
Deportivo. Se utiliza Java 21, Spring Boot 4.1.1 y la misma separación por capas:
`controller`, `service`, `repository`, `model`, `dto`, `exception` y `config`.
Se añaden `websocket` para el canal directo y `tools` para preparar el entorno local.

La sustitución de Firestore por MongoDB es una decisión autorizada por Jorge,
no un requisito del YAML. Debe reflejarse en la memoria de arquitectura del equipo.
Los documentos originales del equipo permanecen intactos.

## 2. Separar los JSON de los documentos de MongoDB

Los DTO mantienen los nombres, enumeraciones, tipos y validaciones del YAML.
Los documentos guardan también información interna que la API no expone:
eventos activos, ajustes, auditoría y versión de concurrencia.

Se distinguen los campos ausentes, cero, false y null. Los valores calculados no
se aceptan en los PUT. Los campos editables se validan después de combinar la
actualización parcial con el estado que ya existe.

## 3. Guardar cambios de forma atómica

Hay tres colecciones: `estadisticas_partidos`, `eventos_estadisticos` y
`ajustes_estadisticos`. Los repositorios usan Spring Data MongoDB.
`MongoConfig` verifica el replica set y crea las colecciones e índices al arrancar.

El índice único `(partidoId, clientEventoId)` impide duplicados incluso ante
peticiones simultáneas. La transacción coordina evento, partido y auditoría.
`@Version` detecta escrituras concurrentes; se reintenta hasta cinco veces si
MongoDB identifica un conflicto transitorio. Un resultado de confirmación incierto
se comunica como 503 para que el cliente consulte el estado antes de insistir.

## 4. Validar referencias en Deportivo

Antes de registrar eventos o correcciones, el cliente HTTP consulta el partido y
los jugadores enviados, incluido `jugadorRelacionadoId`. Se conserva la temporada
y la información del partido obtenida de Deportivo al crear el estado local.
Un recurso desconocido da 404; una indisponibilidad da 503, sin escrituras parciales.

Estadísticas conserva referencias UUID; no crea ni modifica jugadores o partidos
en Deportivo. El marcador completo del encuentro sigue fuera de este servicio.

## 5. Calcular las estadísticas

`Calculos` convierte cada tipo de evento en sus incrementos. Una canasta aumenta
puntos, tiros anotados e intentados; un fallo solo aumenta intentados. Rebotes,
asistencias, pérdidas, robos, tapones y faltas actualizan sus contadores.

Se calculan rebotes totales, tiros de campo, porcentajes, valoración, eFG%, True
Shooting y asistencias/pérdidas. Las fórmulas adoptadas, cuando el contrato no fija
su detalle, son:

- Porcentaje de tiro: `100 × anotados / intentados`; cero sin intentos, según YAML.
- eFG%: `100 × (canastas de campo + 0,5 × triples) / intentos de campo`.
- TS%: `100 × puntos / [2 × (intentos de campo + 0,44 × intentos libres)]`.
- Asistencias/pérdidas: `asistencias / pérdidas`.
- Valoración: puntos + rebotes + asistencias + robos + tapones + faltas recibidas,
  menos tiros de campo fallados, libres fallados, pérdidas, tapones recibidos y
  faltas cometidas.

Las métricas avanzadas devuelven null cuando su denominador es cero. Los
porcentajes de temporada se calculan con los totales, no promediando porcentajes.
La valoración usa la fórmula PIR convencional como decisión de implementación;
el YAML no concreta esa fórmula. Segundos jugados, titularidad y más/menos se
introducen mediante PUT; no se deducen automáticamente de alineaciones.

## 6. Conservar correcciones y permitir anulaciones

El valor final es `suma de eventos activos + ajuste vigente`.
Ejemplo: los eventos suman 10 puntos y un PUT pide 8; el ajuste queda en −2.
Repetir el mismo PUT mantiene −2. Una canasta posterior de 2 deja el total en 10.
También deben corregirse los tiros correspondientes para mantener la coherencia.

Cada corrección de jugador deja autor, fecha, campos y estados anterior/posterior
en la auditoría. Los jugadores y campos omitidos se conservan. Un PUT de varios
jugadores es atómico: si falla uno, no cambia ninguno.

DELETE marca el evento como anulado, conserva su identificador y reconstruye
el partido con eventos activos y ajustes. Anularlo otra vez devuelve 204 sin
incrementar la versión. Un clientEventoId anulado sigue reservado en ese partido.
Los eventos se consultan incluyendo anulados para mantener la trazabilidad.

No se aceptan contadores finales negativos, más aciertos que intentos, puntos
incompatibles con los tiros ni más de cinco faltas cometidas. Una corrección
incoherente devuelve 400; un evento o una anulación incoherentes devuelven 409.

## 7. Completar consultas, acumulados y rankings

Se han conectado las 11 operaciones REST. Se filtran eventos por fecha exclusiva,
jugador y tipo; históricos y acumulados por temporada y competición.
Se puede incluir `detallePartidos`. Los rankings admiten las 19 métricas,
`minimoPartidos` y `limite` del contrato. Se ordenan de mayor a menor y los empates
por UUID, con posiciones consecutivas; métricas indefinidas quedan fuera.

Un jugador cuenta un partido cuando tiene una fila estadística, incluso si esta
ha quedado a cero tras una anulación. No hay convocatoria/minutos automáticos
para decidir otra definición. Los acumulados se calculan sobre los partidos
guardados, incluidos los que están en directo. Se conserva la temporada del alta.
Estas son decisiones explícitas para casos no detallados en el contrato.

El reloj manual de un PUT se conserva en ese estado. Al registrar/anular eventos
se reconstruye desde la secuencia activa: periodo y último tiempo informado del
periodo, sin descontar tiempo por el reloj del servidor. Los eventos de inicio,
descanso y fin determinan `estadoSeguimiento`.

## 8. Proteger las escrituras

Las lecturas son públicas. Spring Security verifica firma RS256, expiración,
emisor, audiencia e identidad UUID. Solo staff/admin pueden escribir. Los errores
de autenticación y permisos son 401 y 403 con el esquema de error común.

Para desarrollo se generan claves y tokens fuera del proyecto. En el sistema del
equipo se debe configurar la clave/JWKS de Usuarios y acordar emisor/audiencia.
No se ha implementado Usuarios ni el Gateway, que son otros microservicios.

## 9. Enviar instantáneas por WebSocket

El servidor acepta suscripción, cancelación y pong; nunca escrituras. Envía el
box score completo, con una versión creciente, después de confirmar cada cambio.
Hay heartbeat y límites de conexiones, suscripciones, tamaño y frecuencia.

La notificación local se envía tras la transacción. Un sondeo de MongoDB cada
segundo permite recuperar cambios de otra instancia o una notificación perdida.
No se añade Redis. Cada cliente descarta versiones antiguas y al reconectar recibe
otra instantánea. La ruta pública de producción requiere configuración de red,
detallada en `websocket.md`.

## 10. Preparar arranque, ejemplos y pruebas

Los scripts preparan MongoDB portátil, su replica set, las claves locales y el
arranque de la aplicación. `prueba.http` muestra todas las operaciones.
`probar-integracion.ps1` ejecuta la batería completa en una base de prueba separada.

Verificación del 4 de noviembre de 2026:

- 69 pruebas: 32 de entrada, 17 de salida contra el YAML, 4 de conversión MongoDB,
  4 de cálculos y 12 de integración HTTP/MongoDB/WebSocket. Cero fallos y omitidas.
- Las de integración comprueban duplicados concurrentes, atomicidad, ajustes,
  anulaciones, permisos y JWT incorrectos, consultas, métricas, filtros, 404/503,
  límites de faltas, heartbeat y notificación de una escritura real.
- La compilación produce `target/estadisticas-0.0.1-SNAPSHOT.jar`.
- El YAML conserva el SHA256 original:
  `A2EBB1F7508A4A119AB8E996E90AC544C0212C8043F822C5F85B1591A7AE6192`.

La comprobación con el Deportivo real y los scripts de arranque se recoge en
`verificacion-local.md`. Los servidores de comprobación utilizan bases separadas.

## Integración con el resto del equipo

El microservicio está implementado y probado localmente. Para el despliegue conjunto
quedan la configuración de JWT de Usuarios, la dirección/credencial de Deportivo,
una instancia MongoDB de despliegue, orígenes CORS y las rutas TLS del Gateway/entrada
WebSocket. No se ha desplegado en la nube ni conectado a un emisor real de Usuarios.

