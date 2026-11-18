package com.asee.estadisticas.dto;

import com.asee.estadisticas.model.*;
import com.fasterxml.jackson.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Representación JSON del esquema EventoEstadistico del contrato. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventoEstadistico(
        @NotNull @Size(max = 100) String clientEventoId,
        @NotNull TipoEventoEstadistico tipo,
        UUID jugadorId,
        UUID jugadorRelacionadoId,
        @Size(max = 100) String jugadaId,
        @NotNull @Min(1) @Max(10) Integer periodo,
        @Min(0) @Max(600) Integer segundosRestantes,
        TipoFalta tipoFalta,
        @Size(max = 500) String observaciones,
        @NotNull UUID eventoId,
        @NotNull UUID partidoId,
        @NotNull Instant registradoEn,
        @NotNull Boolean anulado,
        Instant anuladoEn
) {
}
