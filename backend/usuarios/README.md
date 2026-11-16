# 🛡️ Microservicio de Usuarios, Identidad y Accesos

Este componente centraliza la gestión de la identidad, el control de accesos basados en roles (RBAC) y la autenticación mediante tokens JWT. Se encarga de la validación de credenciales (usando hash Argon2id), mantiene la integridad del estado de socio y registra el historial de actividades (sincronizado de forma asíncrona mediante RabbitMQ).

## ⚙️ Stack Tecnológico
- **Lenguaje:** Java 21
- **Framework:** Spring Boot 3.3.5 (Spring Web, Spring Data JPA, Spring Security, Spring AMQP)
- **Base de Datos:** PostgreSQL 15
- **Mensajería:** RabbitMQ 3
- **Seguridad:** JJWT (Java JWT) & BouncyCastle (Argon2id)
- **Testing:** JUnit 5, Mockito, Testcontainers & MockMvc
- **Infraestructura Local:** Docker Compose V2

---

## 🛠️ Requisitos del Entorno de Desarrollo

Para la correcta ejecución e integración de este servicio en el entorno local, es necesario disponer de:
- Java Development Kit (JDK) 21.
- Maven 3.x.
- Docker Engine y Docker Compose V2 (indispensables para infraestructura y Testcontainers).
- Entorno de desarrollo recomendado: **IntelliJ IDEA Ultimate** (por su integración nativa con Spring y base de datos).

---

## 🔐 Configuración del Entorno (.env)

El microservicio utiliza una clave secreta para firmar y validar los tokens JWT. Por motivos de seguridad, esta clave no está en el código fuente. 

Debes crear un archivo oculto llamado `.env` en la raíz del microservicio (al mismo nivel que `pom.xml`) con el siguiente contenido:

```env
# Clave secreta para firmar los tokens JWT (Longitud mínima de 32 caracteres / 256 bits codificada en Base64)
JWT_SECRET=tu_clave_secreta_aqui

```

*Nota: Tienes un archivo `.env.example` en el repositorio que sirve de plantilla para configurar esto.*

---

## 🐳 Despliegue de la Infraestructura

La capa de persistencia (PostgreSQL) y el bus de eventos (RabbitMQ) están contenerizados para garantizar un entorno determinista.

1. Navegar al directorio raíz del proyecto (donde se ubica el archivo `docker-compose.yml`).
2. Levantar los contenedores en segundo plano ejecutando:
```bash
docker compose up -d

```


3. Verificar que los contenedores `db_usuarios` (puerto 5432) y `rabbitmq_usuarios` (puertos 5672/15672) se encuentran en estado `Up`:
```bash
docker ps

```



---

## 🗄️ Configuración de Persistencia e Inicialización (DDL)

Por motivos de seguridad e integridad del modelo de datos, la propiedad `spring.jpa.hibernate.ddl-auto` está configurada en `none`. El esquema (DDL) es determinista y se inicializa mediante el script `src/main/resources/schema.sql` en el arranque de la aplicación (forzado por `spring.sql.init.mode=always`).

Para interactuar con la base de datos durante el desarrollo, configura tu IDE (ej. IntelliJ Database Tool) con estos parámetros:

* **Host:** `localhost` | **Port:** `5432`
* **User:** `postgres` | **Password:** `password`
* **Database:** `usuarios_db`

---

## 🚀 Ejecución del Microservicio

Una vez validada la infraestructura (Docker) y creado el `.env`, el servicio puede inicializarse directamente desde el IDE ejecutando la clase `UsuariosApplication.java`, o mediante Maven:

```bash
mvn spring-boot:run

```

El servidor embebido Tomcat arrancará en el puerto `8081` (o el especificado en `application.yml`).

---

## 🧪 Testing Automatizado (Unitario & Integración)

El microservicio cuenta con una suite de pruebas de nivel producción dividida en dos capas:

### 1. Pruebas Unitarias (`src/test/java/.../unit`)

Valida la lógica de negocio pura en la capa Service utilizando **Mockito** para simular la base de datos.

* **Cobertura:** Validaciones de registro, cambio de roles, lógicas de caducidad dinámica de socios y manejo de excepciones.

### 2. Pruebas de Integración (`src/test/java/.../integration`)

Certifica el ensamblaje completo del sistema de extremo a extremo.

* Utiliza **Testcontainers** para levantar un contenedor efímero de PostgreSQL real de forma invisible y aislada.
* Utiliza **MockMvc** para simular peticiones HTTP y validar el sistema de seguridad (códigos 200, 201, 403, 404, 409).
* Prueba la **idempotencia** de RabbitMQ verificando que los eventos duplicados chocan contra la restricción `UNIQUE` en base de datos.

**Para ejecutar toda la suite de pruebas (requiere Docker abierto):**

```bash
mvn test
```
### 3. Pruebas End-to-End (E2E) con Bruno
El contrato de la API y el ciclo de vida completo del usuario se validan desde el exterior mediante la herramienta **Bruno** (alternativa ligera y *offline* a Postman). Las peticiones están guardadas como código de texto plano dentro del propio repositorio para evitar conflictos en Git.

**Cómo ejecutar el flujo automatizado:**
1. Abre Bruno y carga la colección `api-usuarios` ubicada en la raíz de este microservicio.
2. Asegúrate de tener seleccionado el entorno **Local** (que define la variable estática `base_url`).
3. Haz clic derecho sobre la carpeta **E2E - Ciclo de Vida Usuario** y selecciona **Run**.
4. Bruno ejecutará secuencialmente las 10 peticiones (Registro -> Login -> Perfil -> Socios -> Actividades -> Borrado). La colección utiliza scripts (`bru.setVar()`) para inyectar dinámicamente el Token JWT y el UUID en la memoria temporal de una petición a otra sin modificar archivos locales.
