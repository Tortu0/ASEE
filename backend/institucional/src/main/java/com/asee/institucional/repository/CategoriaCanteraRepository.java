package com.asee.institucional.repository;

import com.asee.institucional.model.CategoriaCantera;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

public interface CategoriaCanteraRepository extends MongoRepository<CategoriaCantera, UUID> {
}