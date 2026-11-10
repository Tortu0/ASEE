# Modelos de datos y DTO de Estadísticas

El contrato de referencia es `openapi/estadisticas.yaml`. Sus operaciones,
esquemas y ejemplos permanecen sin modificar. El almacenamiento se adapta a
MongoDB conforme a la decisión del equipo.

## Dos representaciones de los datos

Los **modelos**, en `model`, representan lo que guarda MongoDB y la información
interna necesaria para reconstruir las estadísticas. Los **DTO**, en `dto`,
representan los JSON de la API. Esta separación evita exponer ajustes internos,
datos de auditoría o versiones de concurrencia en las respuestas.

## Documentos de MongoDB

| Clase | Colección | Contenido |
|---|---|---|
| `EstadisticasPartidoDocumento` | `estadisticas_partidos` | Un documento por partido, con datos de referencia de Deportivo, estado del seguimiento, reloj, jugadores y versiones. |
| `EventoEstadisticoDocumento` | `eventos_estadisticos` | Acciones individuales o generales, identificador del cliente y fechas de registro y anulación. |
| `AjusteEstadisticoDocumento` | `ajustes_estadisticos` | Historial de correcciones: autor, fecha, campos enviados y estado del jugador antes y después. |

Cada jugador del documento de partido tiene un `EstadoEstadisticoJugador` con:

- `sumaEventos`: los contadores derivados de eventos activos.
- `ajustes`: las diferencias manuales vigentes respecto a esos contadores.
- `manuales`: titularidad, segundos jugados y más/menos.

`ContadoresEstadisticos` se utiliza para la suma y para los ajustes. Un ajuste
puede ser negativo; esto no autoriza que la estadística final sea negativa.
La comprobación del resultado final se implementa en el servicio.

Ejemplo: los eventos suman 10 puntos y se corrige el total a 8. El ajuste de puntos
es -2. Si después se añade una canasta de 2, la suma de eventos pasa a 12 y el
resultado corregido a 10. También deben corregirse los contadores de tiro que
correspondan para mantener la relación entre puntos y canastas.

Un nuevo PUT reemplaza el ajuste de cada campo enviado. Las correcciones se
conservan al reconstruir el partido; el documento de auditoría no se utiliza
como una lista de incrementos que deban sumarse otra vez.

Se distinguen `revision`, para el control de concurrencia de Spring Data, y
`versionPublica`, para las instantáneas WebSocket previstas en la memoria.
La versión pública se incrementa en cada cambio confirmado y se envía por WebSocket.

Los totales, promedios y rankings de temporada tienen DTO de salida. Su cálculo
se realiza a partir de las estadísticas corregidas de los partidos. No se añade otra colección de acumulados que pueda quedar desactualizada.

## DTO de entrada

| DTO | Operación que lo utiliza |
|---|---|
| `EventoEstadisticoCreate` | Registrar una acción de un partido. |
| `EstadisticaJugadorPartidoUpdate` | Corregir uno o varios campos de un jugador. |
| `EstadisticasPartidoUpdate` | Corregir varios jugadores y, opcionalmente, el reloj. |
| `JugadorEstadisticasUpdate` | Elemento de la lista `jugadores` del PUT de partido. |

Las entradas emplean tipos como `Integer` y `Boolean`, sin asignar valores por
defecto a los campos de actualización. Así, `{"puntos":0}` modifica los puntos,
mientras que omitir `puntos` conserva el valor guardado. `{"titular":false}`
también es un cambio explícito.

`periodoActual` y `segundosRestantesPeriodo` permiten null en el contrato. Sus
métodos `tienePeriodoActual()` y `tieneSegundosRestantesPeriodo()` permiten
distinguir un campo ausente de un null enviado expresamente. Estas marcas son
internas y no aparecen como propiedades JSON.

Validaciones implementadas:

- Campos obligatorios, longitudes y límites del YAML.
- Jugador obligatorio en los eventos individuales; los cuatro eventos generales
  pueden omitirlo.
- Rechazo de null explícito en campos que no admiten null, aunque sean opcionales.
- Al menos un campo editable en la actualización individual.
- Lista de jugadores no vacía, elementos válidos y ausencia de identificadores repetidos.
- Rechazo de los campos calculados en las actualizaciones.
- Rechazo de decimales donde se exige un entero, coerciones de texto a números o
  booleanos y números usados como valores de enumeraciones.

La existencia del partido y de los jugadores, los permisos y la relación
`puntos = 2 * tiros2Anotados + 3 * tiros3Anotados + tirosLibresAnotados`
se verifican en la capa de servicio. La coherencia de tiros debe comprobarse
después de combinar el PUT parcial con los datos guardados. Por eso el DTO no
rechaza por sí solo una actualización que envía únicamente `tiros2Anotados`.

## DTO de respuesta

Se han representado todos los esquemas de salida del contrato:

- `EventoEstadistico`.
- `EstadisticaJugadorPartidoBase`, `EstadisticaJugadorPartido` y `EstadisticasPartido`.
- `TotalesTemporada`, `PromediosTemporada` y `EstadisticasJugadorTemporada`.
- `PorcentajesTiro` y `MetricasAvanzadas`.
- `PosicionRanking` y `RankingEstadistico`.
- `ErrorResponse`, que corresponde al esquema `Error` del YAML.

Las estadísticas individuales conservan sus campos planos: `puntos`, `asistencias`,
etc. Los campos calculados están presentes en las respuestas y los obtiene `Calculos`.

Los identificadores son `UUID` y las fechas `Instant`. Las enumeraciones utilizan
exactamente los valores del contrato, como `tiro_2_anotado` o `en_directo`.
Los valores indefinidos de métricas avanzadas se serializan como null. La propiedad
`detallePartidos` se omite cuando su valor es null; el servicio decide si la
rellena según `incluirPartidos`.

## Índices y transacciones

El modelo declara un índice único por `(partidoId, clientEventoId)`. Este diseño
permite reutilizar un identificador de cliente en otro partido y conserva la
restricción después de anular un evento. También se declaran índices de consulta
por temporada, cronología y jugador.

`MongoConfig` crea los índices reales durante el arranque, antes de aceptar
escrituras. El índice único y las transacciones protegen frente a peticiones
concurrentes, además de la comprobación previa del servicio.

Las escrituras que afectan a varias colecciones usan `TransactionTemplate` y
`MongoTransactionManager`. El entorno local arranca como replica set de un nodo;
una instancia standalone no permite las transacciones necesarias.

## Verificación

Las pruebas usan la configuración JSON de Spring Boot y el conversor de Spring
Data MongoDB. Comprueban los casos de entrada, los nombres de las propiedades de
salida contra el YAML, las enumeraciones y la conversión de documentos con UUID,
fechas, anulaciones, ajustes negativos y auditoría.

```powershell
.\mvnw.cmd test
```

Las pruebas de DTO, modelos y cálculos no necesitan servidores. Las 12 pruebas de
integración adicionales usan MongoDB real, un servidor HTTP de Deportivo simulado
y un servidor WebSocket real. Se activan con `scripts/probar-integracion.ps1`.

Referencias técnicas: [tratamiento de null de Jackson](https://javadoc.io/static/com.fasterxml.jackson.core/jackson-annotations/2.21/com/fasterxml/jackson/annotation/JsonSetter.html) y [índices compuestos de Spring Data MongoDB](https://docs.spring.io/spring-data/data-mongo/reference/api/java/org/springframework/data/mongodb/core/index/CompoundIndex.html).

