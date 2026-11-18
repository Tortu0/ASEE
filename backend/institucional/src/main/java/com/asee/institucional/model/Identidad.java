package com.asee.institucional.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "identidad")
public record Identidad(
        @Id String id,
        String nombreOficial,
        String escudoUrl,
        List<String> coloresOficiales
) {
    public static final String ID_UNICO = "club";
}