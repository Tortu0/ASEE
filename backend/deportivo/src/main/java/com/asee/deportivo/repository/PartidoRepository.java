package com.asee.deportivo.repository;

import com.asee.deportivo.model.Partido;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

public interface PartidoRepository
        extends MongoRepository<Partido, UUID>, PartidoRepositoryCustom {
}