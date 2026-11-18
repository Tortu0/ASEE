package com.asee.institucional.repository;

import com.asee.institucional.model.Historia;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HistoriaRepository extends MongoRepository<Historia, String> {
}