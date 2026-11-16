# Microservicio de Estadísticas — ASEE

Implementación de las 11 operaciones REST del contrato, persistencia con Spring
Data MongoDB, cálculos, correcciones auditables, permisos JWT y WebSocket público
de lectura. Java 21 y Spring Boot 4.1.1, siguiendo las capas de Deportivo.

El YAML original se conserva sin cambios en `docs/openapi/estadisticas.yaml`.
MongoDB sustituye a Firestore por decisión de Jorge tras la recomendación del
profesor. Hay que trasladar esta decisión al apartado 2.5 de la memoria del equipo.

## Arranque en Windows

Abre esta carpeta como proyecto Maven en el IDE, con JDK 21. Todos los comandos
siguientes se ejecutan **desde esta carpeta**. El wrapper descarga Maven y las
dependencias la primera vez; no hace falta instalar Maven por separado.

1. Compila: ` .\mvnw.cmd package `.
2. En la primera terminal: ` .\scripts\iniciar-mongodb.ps1 `; déjala abierta.
3. Arranca el microservicio Deportivo de tu compañero en `http://localhost:8082/v1`.
4. En otra terminal: ` .\scripts\iniciar-estadisticas.ps1 `; déjala abierta.
5. Abre [readiness](http://localhost:8085/v1/actuator/health/readiness).
   Debe responder `{"status":"UP"}`.

En este ordenador MongoDB ya está preparado. En otro Windows x64 compatible,
ejecuta primero ` .\scripts\preparar-mongodb.ps1 `: descarga el ZIP oficial de
MongoDB Community 8.0.34 y verifica su SHA256.

Los binarios, datos y registros de MongoDB quedan fuera del repositorio y de
OneDrive, en `%LOCALAPPDATA%\ASEE\estadisticas\mongodb`. Escucha en
`127.0.0.1:27017`. Se utiliza un replica set local de un nodo, `asee-local`, porque
las transacciones deben guardar eventos, estadísticas y auditoría de forma atómica.
El script de Estadísticas lo inicializa si hace falta; no borra los datos.
Esta instancia sin autenticación y de un solo nodo es para desarrollo local.

Para detenerlo, pulsa Ctrl+C primero en la terminal de Estadísticas y después en
la de MongoDB. No arranques dos instancias en el mismo puerto. Si PowerShell
bloquea los scripts descargados, puedes desbloquear los archivos de esta carpeta
con `Get-ChildItem .\scripts\*.ps1 | Unblock-File` tras revisarlos.

## Probar la API

Las consultas GET son públicas. Las escrituras necesitan un JWT válido de
`staff` o `admin`. En desarrollo, el arranque genera una pareja RSA local y un
token de una hora en `%LOCALAPPDATA%\ASEE\estadisticas\tokens\staff.jwt`.
No hay contraseñas ni claves privadas dentro del proyecto.

```powershell
$token = (Get-Content "$env:LOCALAPPDATA/ASEE/estadisticas/tokens/staff.jwt" -Raw).Trim()
$headers = @{ Authorization = "Bearer $token" }
$base = 'http://localhost:8085/v1'
Invoke-RestMethod "$base/temporadas/2026-2027/estadisticas"
```

`prueba.http` contiene las 11 operaciones y datos compatibles con el inicializador
de Deportivo. Sustituye su variable `token` por el token local. Ejecuta las
peticiones individualmente; las escrituras modifican las estadísticas de ejemplo.
Para renovar el token: ` .\scripts\crear-token.ps1 -Rol staff `.

El script de arranque prepara las claves de desarrollo cuando no se ha configurado
un emisor externo. Para ejecutar desde el IDE, configura previamente MongoDB y
`JWT_PUBLIC_KEY` con la URI `file:///C:/.../public.pem`, o `JWT_JWK_SET_URI`.
`spring-boot:run` por sí solo no prepara estas dependencias.

## Operaciones implementadas

Todas usan el prefijo local `/v1`.

| Método | Ruta | Resultado |
|---|---|---|
| GET / PUT | `/partidos/{partidoId}/estadisticas` | Consultar / corregir el box score. |
| GET / PUT | `/partidos/{partidoId}/jugadores/{jugadorId}/estadisticas` | Consultar / corregir un jugador. |
| GET / POST | `/partidos/{partidoId}/eventos` | Listar / registrar eventos. |
| DELETE | `/partidos/{partidoId}/eventos/{eventoId}` | Anular un evento, conservando su historial. |
| GET | `/jugadores/{jugadorId}/estadisticas` | Acumulados de temporada y detalle opcional. |
| GET | `/jugadores/{jugadorId}/partidos` | Histórico filtrado por temporada. |
| GET | `/temporadas/{temporada}/estadisticas` | Estadísticas de todos los jugadores. |
| GET | `/temporadas/{temporada}/lideres` | Rankings por las 19 métricas del YAML. |

Los parámetros, límites y cuerpos exactos están en el YAML. Los PUT son parciales:
omitir conserva, enviar cero o false modifica. Un ajuste de contadores se guarda
como diferencia respecto a los eventos activos y se sustituye al repetir el PUT.
Los minutos y el más/menos son manuales, tal como exige el contrato.

## Configuración

| Variable | Predeterminado / uso |
|---|---|
| `SERVER_PORT` | `8085` |
| `MONGODB_URI` | `mongodb://localhost:27017/?replicaSet=asee-local&serverSelectionTimeoutMS=3000&connectTimeoutMS=3000` |
| `MONGODB_DATABASE` | `estadisticas`; base propia, separada de Deportivo. |
| `DEPORTIVO_BASE_URL` | `http://localhost:8082/v1` |
| `DEPORTIVO_SERVICE_TOKEN` | Opcional, credencial de servicio para las consultas a Deportivo. |
| `JWT_PUBLIC_KEY` | URI de la clave pública PEM del emisor RS256. |
| `JWT_JWK_SET_URI` | URL JWKS del emisor, alternativa a la clave PEM. Tiene prioridad. |
| `JWT_ISSUER` | `asee-local` en desarrollo; emisor de Usuarios en integración. |
| `JWT_AUDIENCE` | `estadisticas` |
| `ALLOWED_ORIGINS` | `http://localhost:*,http://127.0.0.1:*`; lista separada por comas. |

El JWT debe tener firma RS256, `exp`, `iss`, `aud` y `sub` UUID. Se admiten roles
en `rol`, `role` o la lista `roles`; solo staff/admin escriben. La identidad de
`sub` se registra como autor de las correcciones. El ZIP de referencia trae
Usuarios como estructura inicial: su emisión real de JWT aún debe coordinarse
con el equipo. En el despliegue configura su clave pública/JWKS, emisor y audiencia;
no uses las claves ni los tokens locales como identidad de producción.

La instancia MongoDB externa debe permitir transacciones: replica set con nombre
configurado en su URI. Los índices se crean al arrancar. El arranque falla si falta
la clave de validación JWT o la base no permite el funcionamiento requerido.

`readiness` comprueba aplicación y MongoDB; no garantiza que Deportivo responda.
Si Deportivo no está disponible, las nuevas altas/correcciones responden 503 sin
guardar datos. Las estadísticas ya almacenadas siguen siendo consultables.

## Tiempo real

Local: `ws://localhost:8085/v1/ws`. En producción, la entrada de red debe dirigir
`wss://api.club.example/estadisticas/ws` al `/v1/ws` interno **directamente**,
independientemente del Gateway REST. Véase [el protocolo y ejemplo de cliente](docs/websocket.md).

## Pruebas

Sin servidores: ` .\mvnw.cmd test ` ejecuta 57 pruebas; omite las 12 de integración.
Con MongoDB local arrancado e inicializado:

```powershell
.\scripts\probar-integracion.ps1
```

Ejecuta las 69 pruebas, usando una base aleatoria separada que elimina al acabar.
No necesita Deportivo: simula sus respuestas HTTP para probar también 404 y 503.
Para otro replica set, indica `ASEE_TEST_MONGO_URI`. La comprobación real contra
el Deportivo del compañero se documenta aparte en `docs/paso-a-paso.md`.

## Guías

- [Qué se ha hecho y por qué, paso a paso](docs/paso-a-paso.md).
- [Modelos y DTO](docs/modelos-y-dto.md).
- [WebSocket](docs/websocket.md).
- [Contrato original](docs/openapi/estadisticas.yaml).

Referencias: [Spring Boot y MongoDB](https://docs.spring.io/spring-boot/reference/data/nosql.html),
[instalación ZIP oficial de MongoDB](https://www.mongodb.com/docs/v8.0/tutorial/install-mongodb-on-windows-zip/).

