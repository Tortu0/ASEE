# Microservicio Institucional

Spring Boot 4.1 · Java 21 · MongoDB. Puerto **8086**, contexto `/v1`. Contrato: `institucional.yaml`.

## Qué hace

Información del club: historia, identidad (nombre oficial, escudo, colores), junta directiva, instalaciones,
palmarés, cantera y patrocinadores.

## Endpoints

| Recurso | Operaciones |
|---|---|
| `/club/historia` | `GET`, `PUT` |
| `/club/identidad` | `GET`, `PUT` |
| `/club/junta` | `GET`, `PUT` (sustituye la lista completa de miembros) |
| `/club/instalaciones` | `GET`, `POST` |
| `/club/palmares` | `GET`, `POST`; `/{tituloId}`: `PUT`, `DELETE` |
| `/club/cantera` | `GET`, `POST` |
| `/patrocinadores` | `GET` (filtro `nivel`), `POST`; `/{patrocinadorId}`: `GET`, `PUT`, `DELETE` |

## Arrancar

Necesita MongoDB (por ejemplo, `docker compose up -d` desde `backend/entradas`).

```bash
cd backend/institucional
./mvnw spring-boot:run
```

Si las colecciones están vacías carga datos de ejemplo (historia, identidad, junta, instalaciones, palmarés,
cantera y patrocinadores). Se desactiva con `DATOS_EJEMPLO=false`.
`CORS_DEV=true` (por defecto) permite llamar desde el frontend en `localhost`; con el API Gateway no hace falta.

El frontend de compra de entradas usa `GET /club/identidad` para mostrar el nombre del club.

## Variables de entorno

| Variable | Por defecto |
|---|---|
| `MONGODB_URI` | `mongodb://localhost:27017` (base `institucional`) |
| `DATOS_EJEMPLO` | `true` |
| `CORS_DEV` | `true` |

## Pendiente

- Autenticación y roles: ahora mismo el servicio no valida el JWT (lo haría el API Gateway según la memoria).