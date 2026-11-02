package com.asee.institucional.repository;

import com.asee.institucional.model.Identidad;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface IdentidadRepository extends MongoRepository<Identidad, String> {
}