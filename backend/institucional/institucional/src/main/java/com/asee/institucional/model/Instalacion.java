package com.asee.institucional.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.UUID;

@Document(collection = "instalaciones")
public record Instalacion(
        @Id UUID instalacionId,
        String nombre,
        String direccion,
        String comoLlegar,
        Integer capacidad,
        List<String> fotos,
        Double latitud,
        Double longitud
) {
}