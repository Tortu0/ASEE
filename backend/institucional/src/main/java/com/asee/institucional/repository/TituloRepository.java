package com.asee.institucional.repository;

import com.asee.institucional.model.Titulo;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

public interface TituloRepository extends MongoRepository<Titulo, UUID> {
}