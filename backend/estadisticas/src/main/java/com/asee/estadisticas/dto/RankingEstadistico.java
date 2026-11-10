package com.asee.estadisticas.dto;

import com.asee.estadisticas.model.*;
import com.fasterxml.jackson.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Representación JSON del esquema RankingEstadistico del contrato. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RankingEstadistico(
        @NotNull String temporada,
        String competicion,
        @NotNull MetricaRanking metrica,
        @NotNull List<@NotNull @Valid PosicionRanking> jugadores
) {
}
