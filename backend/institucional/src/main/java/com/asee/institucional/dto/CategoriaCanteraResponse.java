package com.asee.institucional.dto;

import com.asee.institucional.model.CategoriaCantera;

import java.util.UUID;

public record CategoriaCanteraResponse(
        UUID categoriaId,
        String nombre,
        String edades,
        String entrenador,
        String horarios
) {
    public static CategoriaCanteraResponse from(CategoriaCantera c) {
        return new CategoriaCanteraResponse(
                c.categoriaId(), c.nombre(), c.edades(), c.entrenador(), c.horarios());
    }
}