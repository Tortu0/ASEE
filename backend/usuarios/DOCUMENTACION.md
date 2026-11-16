# 📚 Documentación Técnica: Microservicio de Usuarios

Esta documentación detalla la arquitectura, el modelo de datos, las reglas de negocio y los endpoints expuestos por el Microservicio de Usuarios. Está diseñada para que cualquier desarrollador pueda entender rápidamente **cómo** funciona el sistema y **por qué** se han tomado ciertas decisiones técnicas.

---

## 🏗️ 1. Arquitectura General

El microservicio está diseñado bajo una **Arquitectura en Capas (Layered Architecture)** para garantizar la separación de responsabilidades:
1. **Controladores (`/controller`):** Interceptan las peticiones HTTP, validan los permisos de acceso mediante cabeceras, y delegan el trabajo.
2. **Servicios (`/service`):** Contienen el núcleo de la lógica de negocio (encriptación, validaciones condicionales, manejo de idempotencia).
3. **Repositorios (`/repository`):** Interfaces de Spring Data JPA que abstraen las consultas SQL.
4. **Entidades y DTOs (`/entity`, `/dto`):** Modelos de persistencia y objetos de transferencia de datos para aislar la base de datos del exterior.

---

## 🧠 2. Decisiones Arquitectónicas Clave

### 2.1. API Gateway Offloading (Autorización)
El microservicio **no** valida criptográficamente el token JWT en cada petición. Delega esta validación al API Gateway.
* **Por qué:** Evita la duplicación de esfuerzo computacional. El Gateway valida el token y reenvía la petición al microservicio inyectando cabeceras ocultas (`X-User-Id` y `X-User-Role`). Los controladores confían en estas cabeceras para aplicar el Control de Acceso Basado en Roles (RBAC).

### 2.2. Esquema de Base de Datos Determinista
Se ha configurado `spring.jpa.hibernate.ddl-auto=none`.
* **Por qué:** En entornos de producción, delegar la creación de tablas al ORM (Hibernate) es peligroso. El esquema se define rígidamente en `src/main/resources/schema.sql` y se ejecuta forzosamente al arrancar mediante `spring.sql.init.mode=always`.

### 2.3. Encriptación Avanzada (Argon2id)
Se utiliza `Argon2id` a través de la librería Bouncy Castle.
* **Por qué:** A diferencia de algoritmos obsoletos como MD5 o rápidos como SHA, Argon2id requiere un alto consumo de memoria RAM de forma intencionada. Esto hace inviable que un atacante utilice GPUs para realizar ataques de fuerza bruta.

### 2.4. Idempotencia en Mensajería (RabbitMQ)
El consumidor de eventos de RabbitMQ (`ActividadListener`) está protegido contra la recepción de mensajes duplicados.
* **Por qué:** En sistemas distribuidos, un fallo de red puede causar que un evento (ej. "Entrada comprada") se entregue dos veces. Se ha creado una restricción `UNIQUE` en PostgreSQL para la combinación `(usuario_id, tipo, referencia_id)`. Si llega un duplicado, la BD lanza una excepción `DataIntegrityViolationException`, que Java captura e ignora de forma silenciosa.

### 2.5. Tareas Programadas (Cron Job) para Socios
Las cuotas de los socios se expiran mediante una consulta masiva lanzada por un `@Scheduled`.
* **Por qué:** Realizar una consulta SQL `UPDATE ... WHERE fecha_caducidad < HOY` a las 00:00 es infinitamente más eficiente que cargar todos los usuarios en memoria para evaluarlos (Lazy Evaluation) o mantener lógicas complejas en el momento de consulta.

---

## 🗄️ 3. Modelo de Datos (PostgreSQL)

El uso de **UUIDs** como clave primaria es estándar en todo el esquema para evitar colisiones al comunicarse con bases de datos aisladas de otros microservicios.

1. **`usuarios`**: Entidad central.
    * Almacena datos básicos, el `email` (único para login) y el `password` (encriptado).
    * Define el `rol` mediante un **Enum estricto** en Java (`visitante`, `socio`, `staff`, `admin`) para evitar inserciones erróneas.
2. **`socios`**: Relación **1 a 1** con `usuarios`.
    * Cumple con el Principio de Responsabilidad Única. Evita que la tabla usuarios se llene de columnas nulas.
    * Tiene restricción `ON DELETE CASCADE`.
3. **`actividades`**: Relación **1 a N** con `usuarios`.
    * Almacena el historial de eventos asíncronos.
    * La columna `referencia_id` **no** es una clave foránea (FK), ya que apunta a registros en bases de datos MongoDB (Microservicios de Compras/Entradas). Actúa como un puntero lógico.

---

## ⚙️ 4. Capa de Servicios (Lógica de Negocio)

### `UsuarioService`
* **`registrarUsuario(UsuarioCreateDTO)`:** Verifica si el email existe (409 Conflict). Asigna rol `visitante` por defecto, encripta la contraseña y guarda.
* **`buscarPorEmail(String)`:** Método de uso interno para el proceso de Login.
* **`obtenerUsuario(UUID)`:** Retorna un `Optional<Usuario>`.
* **`actualizarUsuario(UUID, UsuarioUpdateDTO)`:** Realiza una actualización parcial ignorando los valores nulos (protege datos si el DTO no trae todos los campos).
* **`cambiarRol(UUID, Rol)`:** Validación tipada (Enum) para actualizar accesos.
* **`eliminarUsuario(UUID)`:** Purga al usuario y desencadena la eliminación en cascada de su registro de socio.

### `SocioService`
* **`obtenerEstadoSocio(UUID)`:** Si un usuario existe pero no tiene cuota, no lanza error; fabrica una respuesta en memoria con estado `no_socio` y descuento `0` para mantener la resiliencia del frontend.
* **`actualizarSocio(UUID, SocioUpdateDTO)`:** Envuelto en `@Transactional`. Modifica la cuota, asigna estado `activo`, calcula descuento, y si es su primera vez, actualiza el Rol del usuario base a `socio` en la misma transacción.
* **`revisarCaducidadesDiarias()`:** Cronjob automatizado que ejecuta una actualización masiva SQL en la BD.

### `ActividadService` y `ActividadListener`
* **`ActividadListener.recibirActividad(ActividadEventDTO)`:** Escucha la cola `q.usuarios.actividades`. Transforma el JSON y delega a PostgreSQL el control de duplicados.
* **`ActividadService.obtenerHistorialUsuario(UUID)`:** Recupera el log de actividades ordenado cronológicamente (más recientes primero) mapeando a DTOs.

---

## 📡 5. Diccionario de Endpoints (Controladores)

> **Regla General de Seguridad:** Todos los endpoints asumen la recepción de las cabeceras `X-User-Id` y `X-User-Role` inyectadas previamente por el API Gateway tras validar el token JWT.

### 5.1. Autenticación (`AuthController`)
* **`POST /auth/login`**
    * **Payload:** `{ "email": "...", "password": "..." }`
    * **Operación:** Compara contraseñas usando `passwordEncoder.matches()`. Si es válido, genera JWT firmado con `JWT_SECRET`.
    * **Salidas:** `200 OK` (Con token JWT y perfil público), `401 Unauthorized` (Credenciales inválidas).

### 5.2. Gestión de Usuarios (`UsuarioController`)
* **`POST /usuarios`** (Registro Público)
    * **Payload:** `UsuarioCreateDTO`
    * **Salidas:** `201 Created` (Devuelve cabecera `Location`), `409 Conflict` (Email existente).
* **`GET /usuarios/{id}`** (Consultar Perfil)
    * **Seguridad:** Solo propietario o roles `admin`/`staff`.
    * **Salidas:** `200 OK`, `403 Forbidden`, `404 Not Found`.
* **`PUT /usuarios/{id}`** (Actualizar Perfil Parcial)
    * **Seguridad:** Solo propietario o rol `admin`. Rechaza cuerpo vacío (`400 Bad Request`).
    * **Payload:** `UsuarioUpdateDTO` (nombre, telefono, direccion opcionales).
    * **Salidas:** `200 OK`, `403 Forbidden`, `404 Not Found`.
* **`DELETE /usuarios/{id}`** (Borrar Usuario)
    * **Seguridad:** Exclusivo rol `admin`.
    * **Salidas:** `204 No Content`, `403 Forbidden`, `404 Not Found`.
* **`PUT /usuarios/{id}/rol`** (Cambiar Rol)
    * **Seguridad:** Exclusivo rol `admin`.
    * **Payload:** `{ "rol": "admin" }` (Validado internamente contra Enum `Rol`).
    * **Salidas:** `200 OK`, `400 Bad Request` (Rol no válido), `403 Forbidden`.

### 5.3. Gestión de Socios (`SocioController`)
* **`GET /usuarios/{id}/socio`** (Consultar Cuota)
    * **Seguridad:** Solo propietario o roles `admin`/`staff`.
    * **Salidas:** `200 OK` (Estado real o respuesta fabricada de `no_socio`), `403 Forbidden`, `404 Not Found`.
* **`PUT /usuarios/{id}/socio`** (Alta / Renovación)
    * **Seguridad:** Exclusivo rol `admin`.
    * **Payload:** `SocioUpdateDTO` (tipoCuota, fechaCaducidad).
    * **Salidas:** `200 OK` (Asigna estado `activo` y genera número de socio), `403 Forbidden`.

### 5.4. Historial de Actividades (`ActividadController`)
* **`GET /usuarios/{id}/historial`** (Log de compras)
    * **Seguridad:** Solo propietario o roles `admin`/`staff`.
    * **Salidas:** `200 OK` (Array JSON de eventos), `403 Forbidden`, `404 Not Found`.