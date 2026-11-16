package com.asee.deportivo.repository;

import com.asee.deportivo.model.PreferenciasNotificacion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

public interface PreferenciasRepository extends MongoRepository<PreferenciasNotificacion, UUID> {
}