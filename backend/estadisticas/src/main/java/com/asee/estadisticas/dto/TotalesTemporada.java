package com.asee.estadisticas.dto;

import com.asee.estadisticas.model.*;
import com.fasterxml.jackson.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Representación JSON del esquema TotalesTemporada del contrato. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TotalesTemporada(
        @Min(0) Long segundosJugados,
        @Min(0) Long puntos,
        @Min(0) Long tiros2Anotados,
        @Min(0) Long tiros2Intentados,
        @Min(0) Long tiros3Anotados,
        @Min(0) Long tiros3Intentados,
        @Min(0) Long tirosLibresAnotados,
        @Min(0) Long tirosLibresIntentados,
        @Min(0) Long rebotesOfensivos,
        @Min(0) Long rebotesDefensivos,
        @Min(0) Long rebotesTotales,
        @Min(0) Long asistencias,
        @Min(0) Long robos,
        @Min(0) Long perdidas,
        @Min(0) Long tapones,
        @Min(0) Long taponesRecibidos,
        @Min(0) Long faltasCometidas,
        @Min(0) Long faltasRecibidas,
        Long masMenos,
        Double valoracion
) {
}
