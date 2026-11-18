package com.asee.institucional.repository;

import com.asee.institucional.model.Instalacion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

public interface InstalacionRepository extends MongoRepository<Instalacion, UUID> {
}