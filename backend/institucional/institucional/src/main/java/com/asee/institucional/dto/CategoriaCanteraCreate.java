package com.asee.institucional.dto;

import jakarta.validation.constraints.NotBlank;

public record CategoriaCanteraCreate(
        @NotBlank String nombre,
        String edades,
        String entrenador,
        String horarios
) {
}