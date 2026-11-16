package com.asee.estadisticas.dto;

import com.asee.estadisticas.model.*;
import com.fasterxml.jackson.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Representación JSON del esquema EstadisticasPartido del contrato. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EstadisticasPartido(
        @NotNull UUID partidoId,
        String temporada,
        @NotNull EstadoSeguimientoPartido estadoSeguimiento,
        @Min(1) @Max(10) Integer periodoActual,
        @Min(0) @Max(600) Integer segundosRestantesPeriodo,
        @NotNull List<@NotNull @Valid EstadisticaJugadorPartido> jugadores,
        @NotNull Instant actualizadoEn
) {
}
