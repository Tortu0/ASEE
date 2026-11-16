package com.asee.institucional.dto;

import com.asee.institucional.model.Identidad;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record IdentidadDto(
        @NotBlank String nombreOficial,
        String escudoUrl,
        @NotNull List<@Pattern(regexp = "^#[0-9A-Fa-f]{6}$",
                message = "debe tener formato #RRGGBB") String> coloresOficiales
) {
    public static IdentidadDto from(Identidad i) {
        return new IdentidadDto(
                i.nombreOficial(),
                i.escudoUrl(),
                i.coloresOficiales() == null ? List.of() : i.coloresOficiales());
    }
}