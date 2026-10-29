
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

---

## 📊 Modelo de Datos (Esquema Relacional)

La base de datos PostgreSQL está diseñada siguiendo el principio de aislamiento de microservicios, normalizando la información de identidad y manteniendo referencias débiles hacia dominios externos. Se compone de tres tablas principales:

### 1. Tabla `usuarios`
Es el núcleo de identidad del sistema. Almacena la información personal, las credenciales de acceso y determina los permisos de cada individuo dentro de la plataforma.
* **`id` (UUID):** Clave primaria. Se utiliza UUID en lugar de identificadores secuenciales enteros para garantizar que el ID sea único a nivel global y evitar colisiones cuando los microservicios intercambien información.
* **`email` (UNIQUE):** Actúa como identificador natural (nombre de usuario) para el proceso de *login*, garantizando que no existan cuentas duplicadas.
* **`password`:** Almacena la contraseña del usuario. Por seguridad, nunca se guarda en texto plano, sino que se encriptará utilizando el algoritmo Argon2id (hash + salt).
* **`rol`:** Define el nivel de acceso (RBAC). Los valores permitidos son `visitante`, `socio`, `staff` y `admin`. El API Gateway utilizará este campo incrustado en el token JWT para autorizar o denegar peticiones.

### 2. Tabla `socios`
Gestiona la información de las suscripciones. Se ha diseñado como una tabla separada (relación 1 a 1) en lugar de incluir estas columnas en la tabla `usuarios` para cumplir con el Principio de Responsabilidad Única y evitar que los usuarios base (rol `visitante`) tengan multitud de columnas vacías (nulas).
* **`usuario_id` (UUID - PK & FK):** Es a la vez su identificador y la clave foránea que lo vincula al usuario. Implementa la regla `ON DELETE CASCADE`: si el club da de baja y borra a un usuario, su registro de socio se destruye automáticamente, manteniendo la base de datos limpia.
* **`numero_socio` (UNIQUE):** Identificador comercial amigable (ej. S-000123) para la gestión física e institucional.
* **`estado` y `tipo_cuota`:** Controlan la validez de la suscripción (`activo`, `caducado`) y la periodicidad del cobro.
* **`descuento_porcentaje`:** Valor numérico que los microservicios de *Entradas* y *Compras* consultarán de forma síncrona vía REST para aplicar rebajas en el carrito.

### 3. Tabla `actividades`
Actúa como un registro histórico (log) de las interacciones económicas del usuario con el club. Esta tabla se alimenta de forma asíncrona escuchando eventos en RabbitMQ (ej. "entrada comprada", "pedido creado").
* **`usuario_id` (UUID - FK):** Vincula la actividad con el perfil del comprador (relación 1 a N).
* **`tipo`:** Clasifica la actividad (`entrada` o `pedido`).
* **`referencia_id` (UUID):** Esta es una decisión arquitectónica clave. Almacena el ID de la compra, pero **no** es una clave foránea (`FOREIGN KEY`) a nivel de base de datos. Dado que las entradas y pedidos reales se almacenan en bases de datos MongoDB (aisladas en otros microservicios), PostgreSQL no puede validar su existencia directamente. Actúa como un "puntero lógico" externo.
* **`importe` y `fecha`:** Datos consolidados en el momento de la transacción para mantener un registro inmutable en el perfil del usuario, independientemente de si el precio del producto cambia en el futuro en la tienda.