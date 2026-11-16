package com.asee.estadisticas.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** Estado del partido; los eventos y el historial de ajustes se guardan aparte. */
@Document(collection = "estadisticas_partidos")
@CompoundIndex(name = "temporada_competicion", def = "{'temporada': 1, 'competicion': 1}")
public record EstadisticasPartidoDocumento(
        @Id UUID partidoId,
        String temporada,
        String competicion,
        Integer jornada,
        Instant fechaHora,
        EstadoSeguimientoPartido estadoSeguimiento,
        Integer periodoActual,
        Integer segundosRestantesPeriodo,
        List<EstadoEstadisticoJugador> jugadores,
        Instant actualizadoEn,
        long versionPublica,
        @Version Long revision
) {
    public EstadisticasPartidoDocumento {
        jugadores = List.copyOf(jugadores);
    }
}
