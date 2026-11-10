package com.asee.estadisticas.dto;

import com.asee.estadisticas.model.*;
import com.fasterxml.jackson.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Representación JSON del esquema EstadisticaJugadorPartido del contrato. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EstadisticaJugadorPartido(
        @NotNull UUID partidoId,
        @NotNull UUID jugadorId,
        String temporada,
        String competicion,
        Integer jornada,
        Instant fechaHora,
        Boolean titular,
        @Min(0) Integer segundosJugados,
        @NotNull @Min(0) Integer puntos,
        @Min(0) Integer tiros2Anotados,
        @Min(0) Integer tiros2Intentados,
        @Min(0) Integer tiros3Anotados,
        @Min(0) Integer tiros3Intentados,
        @Min(0) Integer tirosLibresAnotados,
        @Min(0) Integer tirosLibresIntentados,
        @Min(0) Integer rebotesOfensivos,
        @Min(0) Integer rebotesDefensivos,
        @Min(0) Integer rebotesTotales,
        @Min(0) Integer asistencias,
        @Min(0) Integer robos,
        @Min(0) Integer perdidas,
        @Min(0) Integer tapones,
        @Min(0) Integer taponesRecibidos,
        @Min(0) @Max(5) Integer faltasCometidas,
        @Min(0) Integer faltasRecibidas,
        Integer masMenos,
        Double valoracion,
        @Min(0) Integer tirosCampoAnotados,
        @Min(0) Integer tirosCampoIntentados,
        @Valid PorcentajesTiro porcentajes,
        @Valid MetricasAvanzadas metricasAvanzadas
) {
}
