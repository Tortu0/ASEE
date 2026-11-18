package com.asee.deportivo.dto;

import com.asee.deportivo.model.EstadoPartido;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public record PartidoCreate(
        @NotNull Instant fechaHora,
        @NotBlank String rival,
        @NotBlank String lugar,
        Boolean esLocal,
        @NotBlank @Pattern(regexp = "^[0-9]{4}-[0-9]{4}$") String temporada,
        String competicion,
        Integer jornada,
        EstadoPartido estado,
        @Min(0) Integer puntosLocal,
        @Min(0) Integer puntosVisitante,
        Boolean ventaEntradasAbierta
) {
}