-- 1. TABLA USUARIOS
-- Corresponde a los esquemas 'Usuario', 'UsuarioCreate' y 'LoginRequest' de tu OpenAPI.
CREATE TABLE IF NOT EXISTS usuarios (
                                        id UUID PRIMARY KEY,
                                        nombre VARCHAR(150) NOT NULL,
    email VARCHAR(150) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL, -- No está en la respuesta de perfil, pero es vital guardarla (se encriptará con Argon2id)
    telefono VARCHAR(20),
    direccion VARCHAR(255),
    rol VARCHAR(20) NOT NULL,       -- Valores permitidos en tu YAML: visitante, socio, staff, admin
    fecha_alta TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
    );

-- 2. TABLA SOCIOS
-- Corresponde al esquema 'Socio'. Es una extensión 1 a 1 del usuario.
-- Se usa 'ON DELETE CASCADE' para que si borras un usuario, se borre también su registro de socio.
CREATE TABLE IF NOT EXISTS socios (
                                      usuario_id UUID PRIMARY KEY REFERENCES usuarios(id) ON DELETE CASCADE,
    numero_socio VARCHAR(50) UNIQUE,
    estado VARCHAR(20) NOT NULL,    -- Valores permitidos: activo, caducado, no_socio
    tipo_cuota VARCHAR(20),         -- Valores permitidos: mensual, anual, juvenil, jubilado
    fecha_alta DATE,
    fecha_caducidad DATE,
    descuento_porcentaje NUMERIC(5,2)
    );

-- 3. TABLA HISTORIAL DE ACTIVIDADES
-- Corresponde al esquema 'Actividad'. Relación 1 a N (Un usuario, muchas compras/entradas).
CREATE TABLE IF NOT EXISTS actividades (
                                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    tipo VARCHAR(50) NOT NULL,
    referencia_id VARCHAR(100) NOT NULL,
    fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_actividad UNIQUE (usuario_id, tipo, referencia_id)
    );