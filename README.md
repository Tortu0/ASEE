# 🏀 CB Madroñera - Plataforma de Microservicios

Este repositorio contiene la implementación completa de la plataforma de gestión, ticketing y tienda online para el Club Baloncesto Madroñera. 

El proyecto aplica una arquitectura de **microservicios** orquestada mediante contenedores, desarrollada como parte de las Prácticas de la asignatura de Arquitectura de Sistemas y Entornos Empresariales (ASEE).

## 🏗️ Arquitectura y Stack Tecnológico

El ecosistema está compuesto por **6 microservicios de backend** (Java 21 + Spring Boot 3.3), un **API Gateway / Frontend** (Node.js) y una capa de infraestructura distribuida.

*   **Bases de Datos:** PostgreSQL 15 (Usuarios) y MongoDB 7 (Resto de dominios).
*   **Mensajería Asíncrona:** RabbitMQ para eventos de dominio (compras y entradas).
*   **Seguridad:** JWT (JSON Web Tokens) con encriptación HS256 y control de acceso por Roles (Gateway y Servicios).
*   **Despliegue:** Docker y Docker Compose V2.

### Reparto de Dominios (Equipo)
*   **Elena:** Identidad, Seguridad (JWT), API Gateway y Microservicio de **Usuarios** (Puerto `8081`).
*   **Alfonso:** Dominio de Ticketing y Microservicio de **Entradas** (Puerto `8083`).
*   **Adolfo:** E-commerce, Catálogo y Microservicio de **Compras** (Puerto `8084`).
*   **Sergio:** Gestión de Contenido y Microservicios **Deportivo** (`8082`) e **Institucional** (`8086`).
*   **Acedo:** Tiempo Real, WebSockets y Microservicio de **Estadísticas** (Puerto `8085`).

---

## 🚀 Arranque Rápido (Quick Start)

Toda la aplicación está contenerizada para garantizar que funciona exactamente igual en cualquier máquina sin necesidad de instalar Java, Maven o Node.js localmente.

### 1. Configuración de Variables de Entorno
En la raíz del proyecto, asegúrate de tener un archivo llamado `.env` (puedes copiarlo desde `.env.example`). Debe contener las credenciales maestras y la clave secreta de los tokens:

```env
JWT_SECRET=01234567891011121314151617181920
ADMIN_EMAIL=admin@club.test
ADMIN_PASSWORD=Club2026!
```

### 2. Levantar la Infraestructura

Abre una terminal en esta misma carpeta raíz y ejecuta:

```bash
docker compose up -d --build
```

*La primera vez tardará varios minutos mientras descarga las imágenes base y compila el código Java de los 6 microservicios.*

### 3. Acceso a la Plataforma

Una vez que los contenedores estén iniciados, abre tu navegador web en:
👉 **http://localhost:8080**

Puedes comprobar el estado de salud de todos los microservicios y sus conexiones en tiempo real accediendo a: `http://localhost:8080/#/estado`.

---

## 🧪 Pruebas y Auditoría Automática

### Generar datos de prueba (Seed)

Para poder probar todos los flujos de la aplicación (compras, reserva de entradas, etc.), es recomendable generar usuarios de prueba adicionales (Staff y Socio). En otra terminal, ejecuta:

```bash
cd frontend
npm install
ADMIN_EMAIL=admin@club.test ADMIN_PASSWORD=Club2026! npm run seed
```

*Usuarios disponibles (contraseña para todos: `Club2026!`): `admin@club.test`, `staff@club.test`, `socio@club.test`, `visitante@club.test`.*

### Auditoría de Contratos (Verify)

El proyecto incluye un script automatizado que lanza peticiones HTTP reales contra los 6 microservicios levantados en Docker para verificar que cumplen el contrato OpenAPI, respetan la seguridad y se comunican bien por RabbitMQ. Para ejecutarlo:

```bash
cd frontend
ADMIN_EMAIL=admin@club.test ADMIN_PASSWORD=Club2026! STAFF_EMAIL=staff@club.test STAFF_PASSWORD=Club2026! npm run verify
```

---

## 🛑 Detener la Aplicación

Para apagar de forma segura todas las bases de datos, RabbitMQ y los microservicios sin perder los datos guardados:

```bash
docker compose down
```

*(Si quieres borrar también los datos de las bases de datos, añade la flag `-v`: `docker compose down -v`).*

