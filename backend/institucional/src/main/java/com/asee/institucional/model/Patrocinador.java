package com.asee.institucional.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

@Document(collection = "patrocinadores")
public record Patrocinador(
        @Id UUID patrocinadorId,
        String nombre,
        String logoUrl,
        String enlace,
        NivelPatrocinio nivel
) {
}