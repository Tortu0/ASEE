package com.asee.institucional.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

@Document(collection = "palmares")
public record Titulo(
        @Id UUID tituloId,
        String competicion,
        String temporada,
        String descripcion,
        String fotoUrl
) {
}