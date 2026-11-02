package com.asee.institucional.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

@Document(collection = "cantera")
public record CategoriaCantera(
        @Id UUID categoriaId,
        String nombre,
        String edades,
        String entrenador,
        String horarios
) {
}