package com.asee.estadisticas.model;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/** Una anulación conserva el evento y su clientEventoId. */
@Document(collection = "eventos_estadisticos")
@CompoundIndexes({
    @CompoundIndex(name = "partido_client_evento_unico",
            def = "{'partidoId': 1, 'clientEventoId': 1}", unique = true),
    @CompoundIndex(name = "partido_cronologia", def = "{'partidoId': 1, 'registradoEn': 1, '_id': 1}")
})
public record EventoEstadisticoDocumento(
        @Id UUID eventoId,
        UUID partidoId,
        String clientEventoId,
        TipoEventoEstadistico tipo,
        UUID jugadorId,
        UUID jugadorRelacionadoId,
        String jugadaId,
        Integer periodo,
        Integer segundosRestantes,
        TipoFalta tipoFalta,
        String observaciones,
        Instant registradoEn,
        boolean anulado,
        Instant anuladoEn
) {
}
