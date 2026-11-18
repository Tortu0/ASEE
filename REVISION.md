# Revision exhaustiva del proyecto ASEE

**Fecha de revision:** 18 de noviembre de 2026  
**Alcance:** seis microservicios, infraestructura Docker Compose, API Gateway, frontend SPA y pruebas automatizadas.

## 1. Resumen ejecutivo

El proyecto tiene una base tecnica completa y ya demuestra varios flujos reales entre servicios: reserva y compra de entradas, alta de actividad en Usuarios mediante RabbitMQ, validacion de partidos entre Entradas/Estadisticas/Deportivo, JWT compartido y WebSocket operativo.

Sin embargo, **la entrega no esta todavia lista para presentarse sin correcciones**:

1. **Compras no coincide con el contrato OpenAPI aprobado**: devuelve la forma interna de datos en lugar de la forma publica documentada. Esto impide que el frontend muestre productos o complete el flujo de tienda.
2. **Entradas no compila desde Maven con el JDK local** por problemas de procesamiento Lombok/anotaciones. El contenedor Docker Java 21 funciona, pero esto es un riesgo serio en un entorno limpio o al ejecutar las pruebas.
3. **Deportivo e Institucional no hacen validacion JWT propia**. El gateway protege las rutas, pero el puerto de cada servicio sigue expuesto y una llamada directa puede crear/modificar datos sin autenticar.
4. **Las pruebas de Deportivo e Institucional son solamente contextLoads**; no prueban servicios, repositorios ni controladores.
5. **Entradas solo tiene 2 pruebas superficiales/archivo de configuracion** y no tiene pruebas de reserva, pago, validacion ni mensajeria que demuestren su logica.
6. **No existe una suite automatizada del frontend** ni pruebas de rutas, formularios, roles o navegadores. La tienda real aparece vacia si no se crea/siembra catalogo.
7. **Hay 3 vulnerabilidades npm de severidad alta** en las dependencias del frontend/gateway.

### Resultado de la comprobacion integrada

| Comprobacion | Resultado |
|---|---|
| Stack Docker Compose | Todos los contenedores se arrancaron y quedaron operativos tras establecer `.env` y esperar el arranque |
| Puertos y prefijo `/v1` | 6/6 servicios pasaron |
| Contratos publicos principales | 5/6 pasaron; Compras fallo por el modelo de respuesta |
| Autorizacion del gateway | Pasaron validacion por rol, login y rechazo de cabeceras falsas |
| Reserva/compra de entradas | Pasaron reserva, pago, QR y evento a Usuarios |
| Estadisticas | Pasaron validacion del partido y WebSocket |
| Tienda | 12 comprobaciones quedaron omitidas porque no habia productos con stock |
| Smoke test automatizado | 26 pasaron, 1 fallo, 12 omitidos, 1 aviso |

El fallo inicial al levantar Compose fue una consecuencia de utilizar `.env.example` (sin valores) durante la primera ejecucion. La configuracion real existe en `.env`, que no esta versionado.

---

## 2. Matriz global de entrega

| Criterio | Estado | Valoracion |
|---|---|---|
| Logica de negocio y persistencia | Casi completo | Alta |
| Microservicios independientes | Completo | Alta |
| Comunicacion HTTP entre servicios | Completo | Alta |
| Mensajeria RabbitMQ | Completo/validado parcialmente | Alta |
| Seguridad y roles | Bueno en Usuarios/Gateway, incompleto en servicios directos | Alta |
| Frontend e integracion UI | Amplio, pero no valido contra el backend completo | Alta |
| Pruebas unitarias/integracion | Desigual por microservicio | Alta |
| Contenerizacion | Completa y arrancable | Alta |
| Contrato OpenAPI | Practicamente completo, pero con desvios de implementacion | Alta |
| Preparacion final para entrega | No cerrada | Alta |

---

## 3. Analisis por microservicio

## 3.1 Usuarios — puerto 8081

**Tecnologia:** Spring Boot 3.3.5, PostgreSQL/JPA, RabbitMQ, JWT HS256.

**Estado funcional:** Operativo en Docker Compose y probado desde el gateway.

**Persistencia:** PostgreSQL real con entidades JPA. La persistencia cumple el requisito de negocio. Se pudieron registrar usuarios, hacer login y consultar actividad.

**Logica implementada:**

- Registro y login.
- Emision de JWT con `sub` y rol.
- Consulta, actualizacion y borrado de perfiles.
- Estados de socio y alta de cuota.
- Historial de actividad.
- Publicacion/consumo de eventos RabbitMQ.
- Seed inicial de administrador con variables de entorno.

**Pruebas:**

- 52 pruebas ejecutadas, todas pasando con `JWT_SECRET` configurado.
- Hay tests unitarios de Usuario, Socio, Actividad y JWT.
- Hay tests de integracion de controladores y Rabbit/actividades.
- El `contextLoads` fallaba cuando se ejecutaba sin `JWT_SECRET`; tras exportar el secreto, paso.

**Lo que falta o mejorar:**

1. **Alta prioridad: evitar que `JWT_SECRET` sea obligatorio sin fallback seguro solo de tests.**
   - El contexto Spring no arranca si falta la variable.
   - Esto puede afectar al evaluador si arranca con Maven en una carpeta distinta.
   - Conviene documentar claramente el valor esperado y darle un secreto predeterminado exclusivo de test, no de produccion.

2. **Alta prioridad: reforzar el borrado/logica de socio.**
   - Comprobar transacciones y consistencia al eliminar usuarios con entradas/pedidos/actividad asociados.

3. **Media prioridad: completar validaciones negativas.**
   - Email duplicado, password, longitud de nombre/direccion, casos de usuario inexistente.

4. **Media prioridad: actualizar el test de contexto.**
   - Que cargue con un secreto falso controlado por `src/test/resources/application.yaml` o propiedades de test.

**Importancia para la asignatura:** Muy alta. Es la identidad compartida de todos los flujos. Sin usuarios seguros no hay compra, entradas, carrito ni actividad completa.

---

## 3.2 Deportivo — puerto 8082

**Tecnologia:** Spring Boot, MongoDB.

**Estado funcional:** Operativo. Se pudieron consultar partidos, jugadores y noticias. Se pudo crear datos directamente en smoke tests.

**Persistencia:** MongoDB persistente y datos de ejemplo. Cumple persistencia y CRUD basico.

**Logica implementada:**

- Partidos, jugadores y noticias.
- Preferencias de notificacion.
- CRUD con respuestas publicas.
- Integracion con Entradas y Estadisticas.

**Pruebas:**

- Existe un unico test `DeportivoApplicationTests.contextLoads`.
- La prueba pasa, pero no valida ningun servicio ni endpoint.

**Lo que falta o mejorar:**

1. **Critica: seguridad directa.**
   - Las pruebas anonimas pudieron crear partidos/noticias directamente sin pasar por el gateway.
   - Un cliente que conozca el puerto 8082 evade toda autorizacion.
   - El servicio necesita validacion JWT propia o acceso exclusivo de red interna.

2. **Alta: tests unitarios e integracion.**
   - Casos de fechas invalidas, partidos inexistentes, venta de entradas abierta/cerrada.
   - Actualizacion y borrado.
   - Consulta por temporada y filtros.

3. **Media: consistencia de datos.**
   - Validar que `fechaHora`, temporada, jornada y rival sean coherentes.
   - Confirmar transiciones de estado permitidas.

**Importancia para la asignatura:** Muy alta. Es el eje deportivo usado por Entradas y Estadisticas.

---

## 3.3 Entradas — puerto 8083

**Tecnologia:** Spring Boot 3.3.5, MongoDB, RabbitMQ, WebClient, JWT.

**Estado funcional:** Operativo en Docker. La comprobacion integrada paso:

- Consulta de aforo y precios.
- Reserva de asientos.
- Pago/emision.
- QR.
- Recepcion posterior del evento en el historial de Usuarios.

**Persistencia:** Reservas, asientos, tarifas y entradas quedan en MongoDB.

**Pruebas locales:**

- Solo dos clases: `EntradasApplicationTests` y `JwtSecretConfigTest`.
- No hay tests funcionales por servicio/controller/reserva.
- La compilacion Maven falla con el JDK 25 local por `@Slf4j` y Lombok/getters generados que no se resuelven.
- El Docker Java 21 construye y ejecuta el servicio; aun asi hay que comprobar que el build limpio local con Java 21 pasa.

**Lo que falta o mejorar:**

1. **Critica: estabilizar Lombok/build.**
   - Confirmar el resultado exacto usando el JDK 21 configurado por la documentacion/Docker.
   - Poner Lombok como `annotationProcessorPaths` en el maven-compiler-plugin para que el build no dependa del JDK/procesador actual.

2. **Critica: ampliar pruebas.**
   - Reserva atomica y expiracion.
   - Doble reserva del mismo asiento.
   - Pago fallido y cancelacion.
   - Idempotencia.
   - Emision y formato del QR.
   - Validacion staff.
   - Publicacion de eventos.
   - Falla de Deportivo/Usuarios con 503/404.

3. **Alta: seguridad propia.**
   - Validar JWT y propietario fuera del gateway.
   - Evitar que el usuario consulte entradas ajenas cambiando `usuarioId`.

4. **Media: concurrencia.**
   - Probar dos reservas simultaneas sobre el mismo asiento y expiracion con reloj/control de tiempo.

**Importancia para la asignatura:** Muy alta. Reune persistencia, relaciones entre servicios, mensajeria y logica de negocio compleja.

---

## 3.4 Compras — puerto 8084

**Tecnologia:** Spring Boot 4.1.1, MongoDB, RabbitMQ.

**Estado funcional:** Arranca y contiene mucha logica. Tiene 78 tests unitarios/integracion que pasan.

**Persistencia:** MongoDB para productos, carrito y pedidos.

**Pruebas:**

- 78 pruebas pasan, 0 fallos.
- Hay pruebas de dominio, carrito, pedidos, autenticacion, infraestructura y contexto.
- Buena cobertura para la logica interna.

**Desvio confirmado contra el OpenAPI:**

El contrato pide `productoId`, `nombre`, `categoria`, `precio`, `stock[]` y el modelo publico. El controlador existente expone internamente identificador `id`, `stockPorTalla` u otras claves distintas segun el servicio. La verificacion automatizada encontro que el listado no contiene los campos que espera el contrato.

El frontend no puede comprar productos mientras el catalogo este vacio o el payload no coincida.

**Lo que falta o mejorar:**

1. **Critica: armonizar DTO publico.**
   - Crear explicitamente `ProductoDto` con `productoId`, `nombre`, descripcion, categoria, precio, fotos, jugadorId, patrocinadorId y `stock: [{talla, unidades}]`.
   - Mantener entidad/almacenamiento separada del contrato.

2. **Alta: confirmar el flujo completo con datos sembrados.**
   - Crear al menos 2-3 productos en seed/gestion.
   - Comprobar carrito, idempotencia, stock insuficiente, talla inexistente, pedido propietario y reponer stock al cancelar.

3. **Alta: seguridad directa.**
   - Solo el gateway verifica JWT; el servicio tiene `IdentidadResolver` pero tambien se debe revisar autenticacion/autoridad en endpoints escritos.

4. **Alta: integracion Usuarios.**
   - Validar descuento socio y publicar evento pedido.

5. **Media: RabbitMQ.**
   - Las pruebas muestran reintentos ante broker caido, pero conviene testear broker real, confirmaciones y comportamiento ante broker indisponible.

**Importancia para la asignatura:** Muy alta. Es el microservicio con mejor testeo unitario, pero su contrato publico es el unico fallo claro del smoke test real.

---

## 3.5 Estadisticas — puerto 8085

**Tecnologia:** Spring Boot 4.1.1, MongoDB replica set, WebSocket, JWT.

**Estado funcional:** Operativo y probado:

- Consulta por temporada.
- Validacion de partido contra Deportivo.
- Partido inexistente devuelve 404.
- WebSocket acepta suscripcion y devuelve estado.
- Flujo de directos y eventos registrado.

**Pruebas:**

- 61 pruebas pasan.
- 12 pruebas de integracion estan marcadas como skip.
- Hay tests buenos de contratos entrada/salida, calculos, seguridad HMAC y documentos Mongo.

**Lo que falta o mejorar:**

1. **Alta: activar las 12 pruebas de integracion.**
   - Quedan omitidas por falta de entorno externo/condiciones.
   - Deben ejecutarse en CI con MongoDB replica set o Testcontainers.

2. **Alta: documentar y probar los dos modos JWT.**
   - Passthrough HS256 con secreto compartido.
   - Exchange RS256 con clave publica.
   - Cerrar configuraciones ambiguas o validaciones inconsistentes.

3. **Media: carga/concurrencia WebSocket.**
   - Pruebas de suscripcion/cancelacion, reconexion y eventos fuera de orden.

4. **Media: integridad estadistica.**
   - Ampliar pruebas de transacciones Mongo y conflictos de escritura.

**Importancia para la asignatura:** Muy alta. Es el escenario mas complejo: replica set, calculos, WebSocket y transacciones.

---

## 3.6 Institucional — puerto 8086

**Tecnologia:** Spring Boot 4.1.1, MongoDB.

**Estado funcional:** Operativo. Identidad, patrocinadores y demas recursos institucionales responden.

**Persistencia:** MongoDB con colecciones de historia, identidad, junta, instalaciones, palmares, cantera y patrocinadores.

**Pruebas:**

- Solo pasa `InstitucionalApplicationTests.contextLoads`.
- No hay pruebas funcionales.

**Lo que falta o mejorar:**

1. **Critica: seguridad directa.**
   - Se pudo crear historia/identidad directamente sin token.
   - Implementar JWT propio o aislar el puerto del acceso externo.

2. **Alta: tests por servicio/controlador.**
   - Historias, identidad, junta, instalaciones, palmares, cantera y patrocinadores.
   - Casos 404/400/204 y actualizacion completa.

3. **Media: completar frontend/contrato.**
   - El README admite que no hay gestion de historia, identidad, junta, instalaciones ni cantera.
   - Si el profesor exige CRUD completo segun OpenAPI, la gestion UI debe cerrar esos apartados.

**Importancia para la asignatura:** Media-Alta. Puede parecer menor frente a compras/entradas, pero aporta el contenido institucional y demuestra muchos CRUD.

---

## 4. Analisis detallado del frontend

### 4.1 Aspectos positivos

- SPA sin build con enrutamiento por hash.
- Gateway Express que sirve frontend y enruta los servicios.
- Gestion de JWT en `sessionStorage`.
- Validacion de rol en el gateway.
- Proteccion contra cabeceras falsas `X-User-*`.
- QR generado en cliente.
- WebSocket directo a Estadisticas.
- Interfaz responsive y cuidado basico de accesibilidad.
- Manejo centralizado de errores API.
- Estados de carga/error usados en varias paginas.

### 4.2 Funcionamiento observado

En navegador real con el backend Docker activo:

- Inicio carga correctamente.
- Partidos, jugadores, noticias e identidad cargaron datos reales.
- Entradas paso el flujo automatico contra el backend.
- Estadisticas WebSocket paso.
- Tienda muestra correctamente el estado vacio cuando no hay productos, pero la verificacion del contrato falla por el payload de Compras.
- El estado de la tienda no sirve aun como compra completa sin seed y contract fix.

### 4.3 Fallos/limitaciones

1. **No hay seed real de productos.** La UI puede mostrar “No hay productos” y no probar tienda/pedido. Esto es el bloqueo principal del flujo de Compras.
2. **No hay pruebas frontend.** No hay Vitest/Jest/Playwright/Cypress ni script `test`.
3. **Mock y backend usan puertos iguales.** `npm run demo` no puede arrancar si Docker ocupa 8081-8086. Esto es confuso y limita la demostracion offline.
4. **El demo real no esta totalmente reproducible con Docker.** El primer arranque exige `.env`, espera activa y configuracion de JWT.
5. **Gestion incompleta respecto al contrato:** no incluye historia, identidad, junta, instalaciones ni cantera.
6. **Accesibilidad no validada en lectores reales.** Solo se describe cuidado visual/foco; no hay prueba automatizada de `axe`.
7. **No hay persistencia offline ni manejo avanzado de desconexion.**
8. **El QR es generado en navegador, no firmado.** Como unica medida de entrada no evita reproduccion si el codigo tambien se conoce. El servidor valida el identificador de entrada, pero conviene incluir firma/nonce.
9. **El carrito/checkout necesita pruebas visuales.** No se pudo completar en smoke test real por falta de productos.
10. **Vulnerabilidades npm.** `npm install` informa 3 vulnerabilidades altas. Hay que ejecutar auditoria y actualizar sin usar `--force` sin revisar cambios.

**Importancia para la asignatura:** Muy alta. El backend puede estar correcto y aun perder puntos si no hay demo visual fluida, productos sembrados y flujo completo.

---

## 5. Revision de infraestructura

### Docker Compose

Puntos positivos:

- PostgreSQL, MongoDB replica set, RabbitMQ y seis servicios definidos.
- Healthchecks para infraestructura.
- Puertos publicados.
- Volumes persistentes.
- Build independiente por Dockerfile.

Puntos a mejorar:

1. Definir healthchecks para microservicios y no solo `service_started`.
2. No exponer todos los puertos de los microservicios si el acceso esperado es solo gateway.
3. Establecer perfiles/testcontainers o iniciar en el orden correcto.
4. Evitar secretos vacios o mostrar el `.env` real como configuracion.
5. Documentar que el `.env.example` no debe copiarse con valores vacios.
6. Incluir `depends_on` de gateway con healthchecks reales.

### Configuracion

- `.env` no esta versionado y `.env.example` no incluye valores de demostracion.
- Los secretos reales no deben compartirse en memoria, logs o capturas.
- El secreto actual usado en el repo es un secreto de desarrollo; debe rotarse antes de cualquier despliegue real.

---

## 6. Estado exacto de las pruebas

| Microservicio | Unitarias/funcionales | Integracion/contexto | Estado |
|---|---|---:|---|
| Usuarios | 51 unitarias/integracion pasando | 1 contexto pasa con secreto | 52 pasan |
| Compras | 77 unitarias/dominio/infra pasando | 1 contexto pasa | 78 pasan |
| Deportivo | Ninguna logica | 1 contextLoads pasa | 1 pasa |
| Entradas | 2 tests pero build falla local | 0 funcionales | No compila en JDK25 local |
| Estadisticas | 61 pasan | 12 skips | 61 pasan, 12 skips |
| Institucional | Ninguna logica | 1 contextLoads pasa | 1 pasa |

El numero de tests no debe confundirse con calidad. Usuarios y Compras tienen mas peso que la mayoria de contextLoad. Deportivo e Institucional necesitan ampliacion significativa.

---

## 7. Hallazgos respecto a los 3 apartados exigidos

### A. Completar logica de negocio incluyendo persistencia

- **Cumplido parcialmente y fuerte en Usuarios, Entradas, Compras y Estadisticas.**
- Comprar es el bloque pendiente por contrato y seed.
- Seguridad directa pendiente en Deportivo e Institucional.
- Entradas necesita estabilizar build y pruebas de concurrencia.

### B. Implementacion de cliente/UI y pruebas

- **UI amplia e integrada, pero sin pruebas automatizadas frontend.**
- El gateway y la SPA son sustanciales.
- Falta cerrar tienda real, campos gestion incompletos y accesibilidad automatizada.
- Las pruebas unitarias de backend son suficientes solo en parte.

### C. Solucion de problemas y completado de la implementacion ASEE

- La raiz principal visible es el desvio de contrato en Compras.
- El build local de Entradas requiere documentacion/JDK o correccion Lombok.
- La seguridad de puerto directa es un defecto de arquitectura.
- Hay que dejar reproducible el arranque completo en el mismo entorno del evaluador.

---

## 8. Plan prioritario para las proximas 48 horas

### Bloque 0 — Demo reproducible (primera media hora)

- Usar obligatoriamente `.env`.
- Levantar el stack y esperar 30-45 segundos.
- Guardar una lista exacta de usuarios demo.
- Añadir al menos staff, socio y visitante con credenciales conocidas.

### Bloque 1 — Criticos (hacer primero)

1. Arreglar el contrato publico de Compras y actualizar frontend/mock si fuera necesario.
2. Sembrar productos reales con tallas y stock.
3. Corregir el build de Entradas y ejecutar `mvn test` bajo JDK 21.
4. Añadir validacion JWT o aislamiento de red a Deportivo e Institucional.
5. Añadir prueba automatizada de smoke para Compras/pedido en el script `verify`.

### Bloque 2 — Requisitos de entrega

1. Anadir al menos 5-10 tests funcionales reales por Deportivo e Institucional.
2. Anadir tests de reservas/pago/QR a Entradas.
3. Activar o reemplazar las 12 integraciones de Estadisticas.
4. Añadir un test frontend Playwright minimo:
   - carga inicio;
   - login;
   - navegacion partidos;
   - catalogo;
   - carrito;
   - estado sistema.
5. Ejecutar `npm audit` y actualizar dependencias; no usar `--force` sin revisar.

### Bloque 3 — Calidad final

1. Completar la gestion institucional si el contrato exige el CRUD completo.
2. Revisar y limpiar datos de prueba “x”.
3. Verificar que no hay datos personales ni logs de secretos.
4. Anadir un script unico de arranque, test y parada.
5. Preparar una demostracion guiada de 7-10 minutos con entradas y tienda.

---

## 9. Comandos de comprobacion utilizados

```bash
docker compose up -d --build
docker compose ps
cd frontend && npm install
cd frontend && npm run verify
cd backend/compras && bash mvnw test
cd backend/deportivo && bash mvnw test
cd backend/entradas && bash mvnw test
cd backend/estadisticas && bash mvnw test
cd backend/institucional && bash mvnw test
cd backend/usuarios && JWT_SECRET=... bash mvnw test
```

Nota: sustituir el valor de `JWT_SECRET` por el mismo usado en Compose.

---

## 10. Conclusion

El proyecto ya supera una base considerable de entrega: microservicios, persistencia, Docker, gateway, JWT, RabbitMQ, WebSocket, frontend y varios flujos reales. La entrega no debe evaluar solo el numero de servicios, sino que todas las rutas clave y contratos coincidan.

Si se corrigen Compras, Entradas y seguridad directa en Deportivo/Institucional, y se añade una prueba frontend minima junto con seed de tienda, el proyecto quedara mucho mas robusto y defendible en 2 dias.
