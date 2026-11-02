package com.asee.institucional.repository;

import com.asee.institucional.model.Junta;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface JuntaRepository extends MongoRepository<Junta, String> {
}