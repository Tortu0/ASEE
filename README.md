# 🏀 CB Madroñera — Plataforma de Microservicios (Práctica 2 · ASEE)

Implementación completa de la plataforma integral de gestión deportiva, venta de entradas, tienda online, seguimiento estadístico en directo y portal institucional para el **Club Baloncesto Madroñera**.

Este proyecto materializa mediante una **arquitectura distribuida de microservicios** los contratos OpenAPI diseñados en la Práctica 1, desarrollado por el **Equipo GA04** en la asignatura **Arquitecturas Software para Entornos Empresariales (ASEE)** (Escuela Politécnica, Universidad de Extremadura).

> **Nota sobre el entorno de ejecución:** La configuración incluida en este repositorio está preparada para facilitar el despliegue, las pruebas de integración y la evaluación académica en local (publicando los puertos individuales de los microservicios y cargando datos iniciales de ejemplo mediante `DATOS_EJEMPLO=true`).

---

## 🏗️ Arquitectura y Stack Tecnológico

El sistema sigue un enfoque de **adecuación tecnológica y persistencia políglota**, eligiendo cada herramienta en función de los requisitos transaccionales, documentales o de tiempo real de cada dominio:

* **Microservicios de Negocio (Java 21 + Spring Boot):**
  * **Spring Boot 3.3.5:** `Usuarios` (`:8081`) y `Entradas` (`:8083`).
  * **Spring Boot 4.1.1:** `Deportivo` (`:8082`), `Compras` (`:8084`), `Estadísticas` (`:8085`) e `Institucional` (`:8086`).
* **API Gateway y Front-end SPA (Node.js 20+):**
  * Servidor **Express** (`:8080`) con `http-proxy-middleware` y `jose` que sirve estáticamente la SPA, enruta `/servicio/v1/**` y aplica autorización dinámica leyendo los contratos de `docs/openapi/*.yaml`.
  * **Front-end SPA sin paso de compilación (*no-build*):** Desarrollado en JavaScript estándar (ES Modules), HTML5 y CSS3 nativos.
* **Persistencia Políglota:**
  * **PostgreSQL 15 (`Usuarios`):** Garantiza integridad referencial estricta y transacciones ACID entre cuentas, roles, suscripciones de socio e historial de compras.
  * **MongoDB 7 (`Deportivo`, `Entradas`, `Compras`, `Institucional`):** Almacenamiento documental adecuado para agregados anidados (mapas de asientos, stock por tallas, hitos) y operaciones atómicas condicionales a nivel de documento.
  * **MongoDB 7 Replica Set (`Estadísticas`):** Configurado como *replica set* (`asee-local`) para habilitar transacciones multi-documento al consolidar acciones en directo y marcadores.
  * **Supabase Storage (`club-media`):** Almacenamiento de objetos en la nube para fotografías optimizadas en formato `.webp` (plantilla, tienda, noticias, instalaciones y patrocinadores), manteniendo recursos `.svg` locales como respaldo automático.
* **Paradigmas de Comunicación:**
  * **HTTP / REST síncrono:** Consultas externas a través del Gateway y validaciones internas entre microservicios mediante `WebClient` no bloqueante (controlando fallos de dependencia con `503 Service Unavailable`).
  * **Mensajería Asíncrona (RabbitMQ 3):** Desacopla la confirmación de compras (`entrada.comprada` y `pedido.creado` sobre `club.exchange`) de la actualización del historial en `Usuarios`, garantizando idempotencia mediante `eventoId` determinista.
  * **Tiempo Real (WebSocket):** Canal directo de solo lectura en `Estadísticas` (`:8085`) que difunde instantáneas versionadas del partido y *box scores* a los espectadores conectados.
* **Seguridad en Profundidad:**
  * Contraseñas almacenadas con **Argon2id**.
  * Tokens **JWT HS256** emitidos por `Usuarios` y modo de intercambio asimétrico **RS256** (`exchange`) desde el Gateway hacia `Estadísticas`.
  * Doble barrera de validación: el Gateway sanitiza cabeceras (`X-User-*`) y aplica reglas OpenAPI, mientras que cada microservicio Spring Boot incorpora su propio *OAuth2 Resource Server* (`NimbusJwtDecoder`) para verificar firma, caducidad y roles incluso ante llamadas directas a su puerto.
* **Concurrencia e Idempotencia:**
  * Bloqueo atómico condicional de asientos en `Entradas` (`MongoTemplate`) para evitar condiciones de carrera en reservas simultáneas.
  * Caducidad automática de reservas temporales mediante TTL configurable (**10 minutos** por defecto) y tarea programada de liberación.
  * Control de idempotencia mediante cabecera `Idempotency-Key` en la confirmación de pedidos de `Compras`.

---

## 👥 Reparto de Dominios y Componentes (Equipo GA04)

| Componente | Puerto | Responsable | Responsabilidades principales | Persistencia / Integraciones |
| :--- | :---: | :--- | :--- | :--- |
| **API Gateway + SPA** | `8080` | **Elena** | Enrutamiento inverso, validación JWT/OpenAPI, sanitización `X-User-*`, intercambio RS256 y serving de la web | Node.js, Express, `jose`, Supabase Storage |
| **Usuarios** | `8081` | **Elena** | Registro, login, perfiles, roles (`visitante`, `socio`, `staff`, `admin`), cuotas de socio e historial | PostgreSQL 15, RabbitMQ (consumidor), JWT HS256 |
| **Deportivo** | `8082` | **Sergio** | Calendario de partidos, resultados, apertura de venta, plantilla (dorsal único), noticias y preferencias | MongoDB 7, Supabase Storage |
| **Entradas** | `8083` | **Alfonso** | Mapa de asientos, aforo, tarifas, reservas concurrentes con TTL, emisión QR y validación en puerta | MongoDB 7, RabbitMQ (publicador), REST a `Deportivo` y `Usuarios` |
| **Compras** | `8084` | **Adolfo** | Catálogo oficial, stock por tallas, carrito, pedidos idempotentes y ciclo de vida de envíos | MongoDB 7, RabbitMQ (publicador), REST a `Usuarios` |
| **Estadísticas** | `8085` | **Acedo** | Registro/anulación de acciones en vivo, *box scores*, rankings e históricos por temporada | MongoDB 7 (Replica Set), WebSocket, REST a `Deportivo` |
| **Institucional** | `8086` | **Sergio** | Historia, identidad visual, junta directiva, instalaciones, palmarés, cantera y patrocinadores | MongoDB 7, Supabase Storage |

---

## 📁 Estructura del Repositorio

```text
ASEE/
├── backend/
│   ├── usuarios/          # Microservicio de identidad, socios e historial (Spring Boot 3.3.5 + PostgreSQL)
│   ├── deportivo/         # Microservicio deportivo: partidos, plantilla y noticias (Spring Boot 4.1.1 + MongoDB)
│   ├── entradas/          # Microservicio de ticketing, reservas concurrentes y QR (Spring Boot 3.3.5 + MongoDB)
│   ├── compras/           # Microservicio de tienda online y pedidos (Spring Boot 4.1.1 + MongoDB)
│   ├── estadisticas/      # Microservicio de estadísticas y WebSocket en vivo (Spring Boot 4.1.1 + MongoDB RS)
│   └── institucional/     # Microservicio de información corporativa del club (Spring Boot 4.1.1 + MongoDB)
├── frontend/
│   ├── public/            # SPA (HTML5, CSS3, ES Modules, integración con Supabase Storage y respaldos SVG)
│   ├── src/               # Código del API Gateway (Express, proxy OpenAPI, validación e intercambio JWT)
│   ├── scripts/           # Scripts de automatización: seed.mjs (datos de prueba) y verify-backend.mjs
│   └── tests/             # Pruebas extremo a extremo de interfaz con Playwright
├── docs/
│   └── openapi/           # Especificaciones OpenAPI 3.0 de los 6 microservicios (*.yaml)
├── docker-compose.yml     # Orquestación completa de infraestructura, bases de datos y servicios
├── .env.example           # Plantilla de variables de entorno
└── README.md              # Documentación general del proyecto
```

---

## ⚙️ Configuración de Variables de Entorno (`.env`)

El fichero `.env` de la raíz no se versiona en Git por buenas prácticas de seguridad (está excluido en `.gitignore`). Para levantar y evaluar la aplicación completa sin necesidad de crear cuentas externas, crea un archivo `.env` en la raíz del proyecto con el siguiente contenido:

```env
JWT_SECRET=01234567891011121314151617181920
ADMIN_EMAIL=admin@club.test
ADMIN_PASSWORD=Club2026!
SUPABASE_URL=https://kxzgrmajidnzapahngme.supabase.co
SUPABASE_ANON_KEY=sb_publishable_zNLDR9wJypi2AG-QpJK4Yw_1RZGKsRo
```

### Explicación de cada variable

* **`JWT_SECRET`**: Clave secreta compartida (mínimo 32 caracteres) utilizada por `Usuarios` para firmar los tokens **JWT HS256** y verificada tanto por el API Gateway como por los microservicios Spring Boot. Si esta variable está vacía, los servicios rechazan arrancar por seguridad.
* **`ADMIN_EMAIL` y `ADMIN_PASSWORD`**: Credenciales con las que el microservicio `Usuarios` crea automáticamente la cuenta administradora inicial al arrancar por primera vez, permitiendo acceder desde el primer momento al panel de gestión (`/gestion`) y ejecutar el script de sembrado de usuarios.
* **`SUPABASE_URL`**: URL pública del proyecto de **Supabase Storage** que aloja el *bucket* `club-media`, empleada por los inicializadores de datos de ejemplo para enlazar las fotografías reales de jugadores, tienda, noticias, instalaciones y patrocinadores.
* **`SUPABASE_ANON_KEY`**: Clave pública de cliente (*publishable / anon key*) servida por el Gateway en `/config.json` para que el navegador pueda subir nuevas imágenes optimizadas directamente desde los formularios del panel de gestión. *(Es una clave pública diseñada para uso en cliente; nunca se utiliza ni se expone ninguna clave `service_role`).*

---

## 🚀 Arranque Rápido con Docker Compose

### Requisitos previos

* **Docker Engine** con **Docker Compose V2** (`docker compose`).
* *(Opcional)* **Node.js 20+** únicamente si se desean ejecutar desde el anfitrión los scripts `seed`, `verify` o las pruebas E2E con Playwright.
* *(Opcional)* **JDK 21** únicamente si se ejecuta algún microservicio fuera de Docker (cada servicio incluye su propio Maven Wrapper `./mvnw`).

### 1. Levantar todo el sistema

Desde la carpeta raíz del proyecto, una vez creado el `.env` anterior:

```bash
docker compose up -d --build
```

> *Nota: La primera ejecución descargará las imágenes base y compilará los 6 microservicios Java, por lo que puede tardar unos minutos. Puedes comprobar que todos los contenedores están activos con `docker compose ps`.*

### 2. URLs de acceso

* **Aplicación Web (SPA + API Gateway):** [http://localhost:8080](http://localhost:8080)
* **Panel de estado y salud en tiempo real de todos los servicios:** [http://localhost:8080/#/estado](http://localhost:8080/#/estado)
* **Consola de administración de RabbitMQ (solo desarrollo):** [http://localhost:15672](http://localhost:15672) (usuario `guest`, contraseña `guest`)

### 3. Detener la aplicación

Para detener los contenedores conservando los datos persistidos en los volúmenes `pg-data` y `mongo-data`:

```bash
docker compose down
```

Si deseas reiniciar desde cero eliminando también las bases de datos locales:

```bash
docker compose down -v
```

---

## 🧪 Pruebas y Verificación del Sistema

### 1. Crear usuarios de demostración (`seed`)

Con los contenedores levantados, abre una terminal en `frontend/` para generar cuentas de prueba correspondientes a todos los roles del sistema:

```bash
cd frontend
npm ci
ADMIN_EMAIL=admin@club.test ADMIN_PASSWORD=Club2026! npm run seed
```

Esto crea las siguientes cuentas (contraseña común **`Club2026!`**):

* **Administrador:** `admin@club.test` (gestión completa, alta de socios y administración).
* **Personal del club (Staff):** `staff@club.test` (gestión deportiva, partidos en directo, validación de entradas QR y pedidos).
* **Socio activo:** `socio@club.test` (descuentos automáticos en entradas y tienda online).
* **Aficionado registrado:** `visitante@club.test` (compra general, reservas y preferencias).

### 2. Auditoría automatizada de contratos e integración (`verify`)

Ejecuta una batería de peticiones HTTP reales a través del API Gateway contra los 6 microservicios activos para comprobar la conformidad con OpenAPI, el control de roles, la concurrencia en reservas/compras y la mensajería RabbitMQ:

```bash
cd frontend
ADMIN_EMAIL=admin@club.test ADMIN_PASSWORD=Club2026! STAFF_EMAIL=staff@club.test STAFF_PASSWORD=Club2026! npm run verify
```

### 3. Pruebas unitarias y de integración por microservicio (`JUnit 5` / `MockMvc`)

Cada microservicio cuenta con su propia suite de pruebas automatizadas (seguridad JWT, controladores, reglas de negocio en servicios, clientes HTTP y publicadores de eventos). Para ejecutarlas desde la carpeta de cada servicio (`backend/<servicio>`):

```bash
./mvnw test
```

*(En Windows, utilizar `mvnw.cmd test`).*

### 4. Pruebas extremo a extremo de interfaz (`Playwright`)

Para validar los flujos completos sobre navegador real desde `frontend/`:

```bash
cd frontend
npx playwright test
```

---

## 📚 Documentación Detallada por Componente

* **Contratos de API:** [`docs/openapi/`](./docs/openapi/)
* **Usuarios, Identidad y Accesos:** [`backend/usuarios/README.md`](./backend/usuarios/README.md) · [`backend/usuarios/DOCUMENTACION.md`](./backend/usuarios/DOCUMENTACION.md)
* **Deportivo:** [`backend/deportivo/README.md`](./backend/deportivo/README.md)
* **Entradas:** [`backend/entradas/README.md`](./backend/entradas/README.md)
* **Compras:** [`backend/compras/README.md`](./backend/compras/README.md)
* **Estadísticas e Interacción:** [`backend/estadisticas/README.md`](./backend/estadisticas/README.md)
* **Institucional:** [`backend/institucional/README.md`](./backend/institucional/README.md)
* **API Gateway y Front-end SPA:** [`frontend/README.md`](./frontend/README.md)