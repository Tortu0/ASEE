# Microservicio Deportivo

Spring Boot 4.1 · Java 21 · MongoDB. Puerto **8082**, contexto `/v1`. Contrato: `deportivo.yaml`.

## Qué hace

Partidos, jugadores, noticias y preferencias de notificación del club.
Entradas lo consulta (`GET /partidos/{id}`) para saber si un partido existe y tiene la venta abierta
(`ventaEntradasAbierta`).

## Endpoints

| Recurso | Operaciones |
|---|---|
| `/partidos` | `GET` (filtros `estado`, `desde`, `hasta`, `ventaAbierta`, `pagina`, `tamano`), `POST`; `/{partidoId}`: `GET`, `PUT`, `DELETE` |
| `/jugadores` | `GET` (filtros `posicion`, `activo`, `pagina`, `tamano`), `POST`; `/{jugadorId}`: `GET`, `PUT`, `DELETE` |
| `/noticias` | `GET` (filtros `categoria`, `jugadorId`, `pagina`, `tamano`), `POST`; `/{noticiaId}`: `GET`, `PUT`, `DELETE` |
| `/usuarios/{usuarioId}/preferencias-notificacion` | `GET`, `PUT` |

Al crear una noticia, el autor se toma de la cabecera opcional `X-User-Id`.

## Arrancar

Necesita MongoDB (por ejemplo, `docker compose up -d` desde `backend/entradas`).

```bash
cd backend/deportivo
./mvnw spring-boot:run
```

Si las colecciones están vacías carga datos de ejemplo (jugadores, 7 partidos y noticias). Los partidos con venta
abierta son `20000000-0000-0000-0000-000000000004` y `...007`. Se desactiva con `DATOS_EJEMPLO=false`.
`CORS_DEV=true` (por defecto) permite llamar desde el frontend en `localhost`; con el API Gateway no hace falta.

## Probar

`prueba.http` (cliente HTTP de IntelliJ).

## Variables de entorno

| Variable | Por defecto |
|---|---|
| `MONGODB_URI` | `mongodb://localhost:27017` (base `deportivo`) |
| `DATOS_EJEMPLO` | `true` |
| `CORS_DEV` | `true` |

## Pendiente

- Autenticación y roles: ahora mismo el servicio no valida el JWT (lo haría el API Gateway según la memoria).