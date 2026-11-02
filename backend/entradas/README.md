# Microservicio de Entradas

Spring Boot 3.3 · Java 21 · MongoDB · RabbitMQ. Puerto **8083**, contexto `/v1`. Contrato: `entradas.yaml`.

## Qué hace

- Mapa de asientos, aforo y precios de un partido (públicos).
- Reserva temporal de asientos (10 min), compra de entradas con código QR, validación en el acceso y anulación (staff).
- Evita vender el mismo asiento dos veces: cada cambio de estado es una operación atómica de MongoDB.
- Consulta a **Deportivo** (`GET /partidos/{id}`) para comprobar que el partido existe y tiene la venta abierta.
- Consulta a **Usuarios** (`GET /usuarios/{id}/socio`) para el descuento de socio, reenviando el JWT del comprador.
- Publica el evento `entrada.comprada` en RabbitMQ (exchange `club.exchange`) por cada entrada vendida.

## Arrancar

```bash
cd backend/entradas
docker compose up -d                      # MongoDB + RabbitMQ
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Necesita Deportivo en marcha (puerto 8082). El perfil `dev` acepta JWT sin verificar la firma y activa CORS:
solo para desarrollo.

Al arrancar carga los asientos y tarifas de los partidos definidos en `entradas.carga` del `application.yaml`
(los mismos que los datos de ejemplo de Deportivo con venta abierta).

## Variables de entorno

| Variable | Por defecto |
|---|---|
| `MONGODB_URI` | `mongodb://localhost:27017/entradas` |
| `RABBIT_HOST` / `RABBIT_USER` / `RABBIT_PASS` | `localhost` / `guest` / `guest` |
| `DEPORTIVO_URL` | `http://localhost:8082/v1` |
| `USUARIOS_URL` | `http://localhost:8081/v1` |
| `JWT_ISSUER_URI` | `http://localhost:8080/realms/club` |

## Pendiente

- Pasarela de pago (cobro, reembolsos).
- Validación real del JWT (algoritmo y claves a acordar con Usuarios / API Gateway).
- Decidir si el descuento de socio se suma a la tarifa `socio` o es alternativo.