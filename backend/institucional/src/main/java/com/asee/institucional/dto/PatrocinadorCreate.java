package com.asee.institucional.dto;

import com.asee.institucional.model.NivelPatrocinio;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PatrocinadorCreate(
        @NotBlank String nombre,
        String logoUrl,
        String enlace,
        @NotNull NivelPatrocinio nivel
) {
}