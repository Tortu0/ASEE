package com.asee.estadisticas.dto;

import com.asee.estadisticas.model.*;
import com.fasterxml.jackson.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Representación JSON del esquema PosicionRanking del contrato. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PosicionRanking(
        @NotNull @Min(1) Integer posicion,
        @NotNull UUID jugadorId,
        @NotNull Double valor,
        @Min(0) Integer partidosJugados
) {
}
