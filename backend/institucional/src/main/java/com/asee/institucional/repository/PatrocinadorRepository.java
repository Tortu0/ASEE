package com.asee.institucional.repository;

import com.asee.institucional.model.NivelPatrocinio;
import com.asee.institucional.model.Patrocinador;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface PatrocinadorRepository extends MongoRepository<Patrocinador, UUID> {

    // Para el filtro opcional ?nivel= del listado de patrocinadores
    List<Patrocinador> findByNivel(NivelPatrocinio nivel, Sort sort);
}