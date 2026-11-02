package com.asee.institucional.dto;

import jakarta.validation.constraints.NotBlank;

public record TituloCreate(
        @NotBlank String competicion,
        @NotBlank String temporada,
        String descripcion,
        String fotoUrl
) {
}