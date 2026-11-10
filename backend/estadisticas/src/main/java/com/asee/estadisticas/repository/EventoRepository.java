package com.asee.estadisticas.repository;

import com.asee.estadisticas.model.EventoEstadisticoDocumento;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EventoRepository extends MongoRepository<EventoEstadisticoDocumento, UUID> {
    List<EventoEstadisticoDocumento> findByPartidoIdOrderByRegistradoEnAsc(UUID partidoId);
    boolean existsByPartidoIdAndClientEventoId(UUID partidoId, String clientEventoId);
    Optional<EventoEstadisticoDocumento> findFirstByPartidoIdOrderByRegistradoEnDesc(UUID partidoId);
}
