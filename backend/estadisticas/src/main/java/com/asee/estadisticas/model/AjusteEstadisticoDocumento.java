package com.asee.estadisticas.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** Auditoría de cada corrección manual: campos enviados y estados antes/después. */
@Document(collection = "ajustes_estadisticos")
@CompoundIndex(name = "ajustes_jugador_partido", def = "{'partidoId': 1, 'jugadorId': 1, 'registradoEn': 1}")
public record AjusteEstadisticoDocumento(
        @Id UUID ajusteId,
        UUID partidoId,
        UUID jugadorId,
        UUID usuarioId,
        Instant registradoEn,
        long versionPartido,
        Set<CampoEditableEstadistica> camposEnviados,
        EstadoEstadisticoJugador anterior,
        EstadoEstadisticoJugador posterior
) {
    public AjusteEstadisticoDocumento {
        camposEnviados = Set.copyOf(camposEnviados);
    }
}
