# Canal WebSocket

Conexión local: `ws://localhost:8085/v1/ws`.
Conexión pública prevista por la memoria: `wss://api.club.example/estadisticas/ws`.
La entrada de red dirige esta última al `/v1/ws` interno de Estadísticas, conservando
la actualización de protocolo WebSocket y el TLS exterior. No pasa por el Gateway REST.
El dominio del contrato es ilustrativo, no un despliegue existente.

## Mensajes

```json
{"tipo":"suscribir","partidoId":"20000000-0000-0000-0000-000000000001"}
```

La respuesta contiene `tipo: "estado"`, `partidoId`, `version` y `datos` con el
esquema completo `EstadisticasPartido`. Un partido válido sin estadísticas empieza
en versión 0. Cada escritura confirmada incrementa la versión y envía otra foto.
Una segunda anulación del mismo evento no cambia el estado ni la versión.

```json
{"tipo":"cancelar_suscripcion","partidoId":"20000000-0000-0000-0000-000000000001"}
```

Cada 30 segundos el servidor envía `{"tipo":"heartbeat"}`. El cliente responde
`{"tipo":"pong"}`. Tras 90 segundos sin pong se cierra la conexión.
Errores: `{"tipo":"error","codigo":"...","mensaje":"..."}`. Códigos:
`mensaje_invalido`, `partido_no_encontrado`, `limite_suscripciones`; si Deportivo
no puede comprobar una suscripción se añade `servicio_no_disponible`.

## Cliente mínimo

Úsalo desde el frontend servido en localhost. En despliegue cambia la URL a WSS
y configura `ALLOWED_ORIGINS` con el origen real del frontend.

```javascript
const partidoId = '20000000-0000-0000-0000-000000000001';
let ultimaVersion = -1;
const socket = new WebSocket('ws://localhost:8085/v1/ws');
socket.onopen = () => socket.send(JSON.stringify({tipo: 'suscribir', partidoId}));
socket.onmessage = ({data}) => {
  const mensaje = JSON.parse(data);
  if (mensaje.tipo === 'heartbeat') {
    socket.send(JSON.stringify({tipo: 'pong'}));
  } else if (mensaje.tipo === 'estado' && mensaje.version > ultimaVersion) {
    ultimaVersion = mensaje.version;
    console.log('Box score completo:', mensaje.datos);
  } else if (mensaje.tipo === 'error') {
    console.error(mensaje.codigo, mensaje.mensaje);
  }
};
```

Al reconectar, vuelve a suscribirte y recupera la foto completa. Usa espera
creciente entre reconexiones. Si el canal falla, consulta el GET del partido.
El servidor puede agrupar cambios rápidos en la última foto; no es un flujo de
eventos que obligue a entregar todas las versiones intermedias. La auditoría de
acciones se obtiene por REST en `/partidos/{id}/eventos`.

## Límites y varias instancias

Valores por instancia, configurables con propiedades de Spring:

| Propiedad | Valor |
|---|---|
| `app.websocket.max-connections` | 100 |
| `app.websocket.max-subscriptions` | 5 por conexión |
| `app.websocket.max-messages-per-second` | 20 por conexión |
| `app.websocket.heartbeat-ms` | 30000 |
| `app.websocket.poll-ms` | 1000 |

Mensajes de entrada de hasta 4 KiB; buffer de salida de 64 KiB y límite de envío
de 5 segundos. Se cierran conexiones que superan límites de mensajes o no responden.
El sondeo permite detectar cambios realizados por otras instancias en el mismo
MongoDB. Los límites de conexión son locales, no una cuota global de todo el clúster.
