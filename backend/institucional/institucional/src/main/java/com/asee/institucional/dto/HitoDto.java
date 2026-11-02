package com.asee.institucional.dto;

import com.asee.institucional.model.Hito;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record HitoDto(
        @NotNull Integer anio,
        @NotBlank String descripcion
) {
    public static HitoDto from(Hito h) {
        return new HitoDto(h.anio(), h.descripcion());
    }
}