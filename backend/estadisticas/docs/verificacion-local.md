# Verificación local — 4 de noviembre de 2026

## Compilación y pruebas automáticas

Java 21.0.10, Spring Boot 4.1.1, MongoDB Community 8.0.34, replica set `asee-local`.

Resultado de `package` con `ASEE_INTEGRATION=true`:

```text
ContratoEntradaTest:          32 correctas
ContratoSalidaTest:           17 correctas
DocumentosMongoTest:           4 correctas
CalculosTest:                  4 correctas
EstadisticasIntegrationTest:  12 correctas
TOTAL:                       69 correctas, 0 fallos, 0 errores, 0 omitidas
BUILD SUCCESS
```

Las pruebas de integración usan HTTP y WebSocket reales de Estadísticas, MongoDB
real y un servidor HTTP controlado que simula Deportivo. Incluyen el rechazo
atómico de contadores que desbordarían los campos calculados.

## Comprobación adicional con Deportivo real

Se compiló y arrancó el código original de Deportivo incluido en el ZIP del
compañero, sin modificar sus fuentes, en el puerto 8082. Se usaron sus datos de
ejemplo: el jugador Carlos Gómez y el primer partido de la temporada 2026-2027.

Estadísticas se arrancó con `scripts/iniciar-estadisticas.ps1` en el puerto 8085.
Se comprobó también `scripts/crear-token.ps1` para renovar el JWT local.
Las bases de ambos servicios se separaron de las del usuario:

- `asee_smoke_deportivo_20261104`.
- `asee_smoke_estadisticas_20261104`.

Se completaron 18 comprobaciones HTTP, cubriendo las 11 operaciones:

1. Readiness: 200, `UP`.
2. Consulta real de partido y jugador a Deportivo: ambas 200.
3. Registro sin token: 401.
4. Registro de canasta con token staff: 201 y 2 puntos.
5. Repetición del mismo clientEventoId: 409.
6. Consulta individual: 200 y 2 puntos.
7. Corrección individual de puntos/tiros: 200 y 4 puntos.
8. Consulta del box score: 200 y 4 puntos.
9. Acumulados, histórico, temporada y ranking: 200 y resultados coherentes.
10. PUT del partido y lista de eventos: 200.
11. Anulación del evento: 204; queda el ajuste de 2 puntos en la consulta posterior.
12. Segunda anulación: 204, sin restar de nuevo.

Los servidores de comprobación se detuvieron al finalizar y se retiraron las dos
bases temporales de esta comprobación. El MongoDB portátil y las claves locales
quedan preparados para el siguiente arranque. El proyecto no se deja ejecutándose
en segundo plano; las instrucciones de arranque están en el README.

## Alcance

El contrato OpenAPI permanece idéntico al original. Hay ejecutable JAR y proyecto
Maven completo. La autenticación se ha probado con firmas RSA y tokens locales;
no con el emisor real de Usuarios, todavía no implementado en el ZIP recibido.
No se ha desplegado en la nube ni configurado el Gateway del equipo.
