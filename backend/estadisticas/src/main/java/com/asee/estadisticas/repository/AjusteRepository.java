package com.asee.estadisticas.repository;

import com.asee.estadisticas.model.AjusteEstadisticoDocumento;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AjusteRepository extends MongoRepository<AjusteEstadisticoDocumento, UUID> {
}
