package com.asee.estadisticas.repository;

import com.asee.estadisticas.model.EstadisticasPartidoDocumento;
import java.util.List;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PartidoRepository extends MongoRepository<EstadisticasPartidoDocumento, UUID> {
    List<EstadisticasPartidoDocumento> findByTemporada(String temporada);
}
