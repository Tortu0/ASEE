package com.asee.estadisticas.dto;

import com.asee.estadisticas.model.*;
import com.fasterxml.jackson.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Representación JSON del esquema EstadisticasJugadorTemporada del contrato. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EstadisticasJugadorTemporada(
        @NotNull UUID jugadorId,
        @NotNull String temporada,
        String competicion,
        @NotNull @Min(0) Integer partidosJugados,
        @Min(0) Integer titularidades,
        @NotNull @Valid TotalesTemporada totales,
        @NotNull @Valid PromediosTemporada promedios,
        @NotNull @Valid PorcentajesTiro porcentajes,
        @Valid MetricasAvanzadas metricasAvanzadas,
        List<@NotNull @Valid EstadisticaJugadorPartido> detallePartidos
) {
}
