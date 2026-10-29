
# Microservicio de Usuarios, Identidad y Accesos

Este componente centraliza la gestión de la identidad, el control de accesos basados en roles (RBAC) y la autenticación mediante tokens JWT. Se encarga de la validación de credenciales (usando hash Argon2id) y mantiene la integridad del estado de socio y el historial de compras y reservas de los usuarios.

## ⚙️ Stack Tecnológico
* **Lenguaje:** Java 17 / 21
* **Framework:** Spring Boot 3.x (Spring Web, Spring Data JPA, Spring Security)
* **Base de Datos:** PostgreSQL 15
* **Infraestructura Local:** Docker Compose V2

---

## 🛠️ Requisitos del Entorno de Desarrollo

Para la correcta ejecución e integración de este servicio en el entorno local, es necesario disponer de:
* Java Development Kit (JDK) 17 o superior.
* Maven 3.x.
* Docker Engine y Docker Compose V2.
* Entorno de desarrollo recomendado: **IntelliJ IDEA Ultimate** (por su integración nativa con Spring y herramientas de bases de datos).

---

## 🐳 Despliegue de la Infraestructura (Base de Datos)

La capa de persistencia está contenerizada para garantizar la paridad entre entornos y aislar las dependencias del sistema operativo base.

1. Navegar al directorio raíz del proyecto (donde se ubica el archivo `docker-compose.yml`).
2. Levantar el contenedor de PostgreSQL en segundo plano ejecutando:
   ```bash
   docker compose up -d

3. Verificar que el contenedor `db_usuarios` se encuentra en estado `Up` expuesto en el puerto `5432`:
    ```bash
    docker ps
    ```



---

## 🗄️ Configuración de Persistencia y Conexión IDE

Por motivos de seguridad e integridad del modelo de datos, la propiedad `spring.jpa.hibernate.ddl-auto` está configurada en `none`. Esto desactiva la generación automática del esquema por parte de Hibernate. El esquema (DDL) es determinista y se inicializa mediante el script `src/main/resources/schema.sql` en el arranque de la aplicación.

Para interactuar con la base de datos y auditar las tablas durante el desarrollo, es necesario configurar el Data Source en IntelliJ IDEA:

1. Abrir la herramienta **Database** (panel lateral derecho en IntelliJ).
2. Seleccionar **`+`** > **Data Source** > **PostgreSQL**.
3. Configurar los parámetros de conexión para que coincidan con las variables de entorno de Docker:
* **Host:** `localhost` (o `127.0.0.1`)
* **Port:** `5432`
* **User:** `postgres`
* **Password:** `password`
* **Database:** `usuarios_db`


4. Descargar los drivers de PostgreSQL requeridos por el IDE si el sistema lo solicita (botón *Download* en el aviso inferior).
5. Ejecutar **Test Connection** para asegurar que el IDE tiene acceso al contenedor. Al obtener un resultado satisfactorio, guardar la configuración.

---

## 🚀 Ejecución del Microservicio

Una vez validada la conexión a la base de datos, el servicio puede inicializarse directamente desde el IDE ejecutando la clase principal `UsuariosApplication.java`, o mediante la CLI de Maven:

```bash
mvn spring-boot:run

```

El servidor embebido Tomcat arrancará por defecto en el puerto `8081` (o el especificado en `application.yml`).
