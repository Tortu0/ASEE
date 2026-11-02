package com.asee.deportivo.dto;

import com.asee.deportivo.model.Posicion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record JugadorCreate(
        @NotBlank String nombre,
        @NotNull @Min(0) @Max(99) Integer dorsal,
        @NotNull Posicion posicion,
        Integer alturaCm,
        LocalDate fechaNacimiento,
        String fotoUrl,
        Boolean activo
) {
}