package com.asee.institucional.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "junta")
public record Junta(
        @Id String id,
        List<MiembroJunta> miembros
) {
    public static final String ID_UNICO = "club";
}