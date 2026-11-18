# Frontend, gateway de desarrollo y verificación — CB Madroñera

Tres piezas en esta carpeta:

| Carpeta | Qué es |
|---|---|
| `public/` | El frontend: SPA sin paso de build (HTML + ES modules + CSS), tipografías autoalojadas. |
| `gateway/` | API Gateway de desarrollo (memoria §3.1). Sirve el frontend y enruta `/{servicio}/v1/**`. |
| `mock/` | Mock de los 6 servicios **fiel al contrato OpenAPI** (no al código actual). Para demos y desarrollo del frontend. |
| `scripts/verify-backend.mjs` | Verificación del backend real: contrato + flujos entre microservicios. |

Informe del análisis del backend: [`../docs/INFORME_VERIFICACION_BACKEND.md`](../docs/INFORME_VERIFICACION_BACKEND.md).

## Arranque rápido (demo con mock, sin Java)

```bash
cd frontend
npm install
npm run demo          # mock de los 6 servicios + gateway + frontend
# → http://localhost:8080
```

Usuarios de prueba (clave `Club2026!`): `admin@club.test`, `staff@club.test`, `socio@club.test`, `visitante@club.test`.
El mock incluye un partido **en juego** (jornada 5) para probar el directo por WebSocket: entra como staff en *Gestión → Partido en directo*, registra un evento y míralo aparecer en la página del partido desde otra pestaña.

## Arranque contra el backend real

Guía completa paso a paso: [`../docs/COMO_PROBAR_TANDA1.md`](../docs/COMO_PROBAR_TANDA1.md). Resumen:

1. `cd infra && docker compose up -d` (PostgreSQL, MongoDB replica set, RabbitMQ).
2. Exporta el **mismo** `JWT_SECRET` (≥ 32 caracteres) y arranca Usuarios (con `ADMIN_EMAIL`/`ADMIN_PASSWORD`), Deportivo, Institucional, Entradas y Estadísticas (`./mvnw spring-boot:run` en cada uno, `.\mvnw.cmd` en Windows) y Compras (ver `COMO_PROBAR_TANDA2.md`).
3. `STATS_TOKEN_MODE=passthrough npm start` (gateway + frontend en http://localhost:8080) y `npm run seed` (crea staff y socio de prueba).
4. `ADMIN_EMAIL=… ADMIN_PASSWORD=… npm run verify`.

Alternativa sin cambiar Estadísticas: no definas `JWT_SECRET` en ella, arranca el gateway sin `STATS_TOKEN_MODE` y pasa a Estadísticas `JWT_PUBLIC_KEY=file:<ruta>/gateway/keys/public.pem` (el gateway reemite un RS256 con `iss`/`aud`).

Variables del gateway: `PORT` (8080), `JWT_SECRET` (obligatoria), `STATS_TOKEN_MODE` (`exchange` | `passthrough`), `UPSTREAM_HOST` (localhost), `WS_URL` (`ws://localhost:8085/v1/ws`), `RATE_PER_MIN` (600).

## Qué hace el gateway

- Enruta `/{servicio}/v1/**` → `http://localhost:{puerto}/v1/**`.
- Valida el JWT de Usuarios (HS256/384/512) y aplica el nivel de acceso de cada operación del contrato (`gateway/rules.js`: público / autenticado / staff / admin; preferencias de notificación solo del propio usuario).
- Descarta las cabeceras `X-User-*` del cliente e inyecta las verificadas.
- Para Estadísticas: o reemite un token RS256 con `iss` y `aud` (`exchange`) o reenvía el HS256 de Usuarios (`passthrough`, con Estadísticas en modo `JWT_SECRET`).
- Limita peticiones por IP. Expone `/_gateway/health` (lo usa la página *Estado del sistema*).
- El tiempo real (WebSocket) va directo a Estadísticas, como indica la memoria.

**Es una pieza de desarrollo.** No sustituye la validación en cada servicio (Deportivo e Institucional hoy no validan nada; Compras valida el Bearer si se le define `JWT_SECRET`).

## Frontend

Rutas (`#/…`): inicio, partidos y detalle con estadísticas y directo, compra de entradas con mapa de asientos y reserva de 10 min, plantilla y ficha de jugador, noticias, estadísticas (líderes y tabla), tienda y carrito, el club, cuenta (perfil, socio, entradas con QR, pedidos, actividad, avisos), gestión (staff/admin) y estado del sistema.

Decisiones:
- **Tema desde el backend**: nombre y color principal salen de `GET /club/identidad`.
- **Sin build**: abre cualquier cambio con solo recargar. Las únicas librerías son `qrcode-generator` (vendorizada, MIT) y las fuentes.
- **Sesión**: el token vive en `sessionStorage` (se pierde al cerrar la pestaña). Todo lo demás se pide al backend.
- **Errores**: normaliza `{codigo, mensaje, detalles}` y los formatos por defecto de Spring.
- El frontend sigue el **contrato**. En el pago envía la cabecera `Idempotency-Key` (la reutiliza al reintentar con los mismos datos, para no cobrar dos veces).

Gestión incluye altas/ediciones/bajas de partidos, jugadores, noticias, productos, patrocinadores y palmarés; cambio de estado de pedidos; registro y anulación de eventos de estadísticas en directo; validación de entradas; y (admin) usuarios, roles y alta de socio.

## Limitaciones conocidas

- No hay edición de historia, identidad, junta, instalaciones ni cantera en la gestión (el contrato lo permite; no lo he incluido).
- La generación del QR es en el cliente a partir de `codigoQr`; el escáner de acceso se simula pegando el identificador de la entrada.
- No se ha probado en un navegador real distinto de Chromium ni con lectores de pantalla; sí se ha cuidado el contraste, el foco visible, las etiquetas de formularios y `prefers-reduced-motion`.
- El frontend se ha probado contra el mock y el gateway, **no contra los servicios Java reales** (no pude ejecutarlos).
