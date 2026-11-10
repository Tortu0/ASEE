package com.asee.estadisticas.dto;

import com.asee.estadisticas.model.*;
import com.fasterxml.jackson.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Representación JSON del esquema PorcentajesTiro del contrato. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PorcentajesTiro(
        @Min(0) @Max(100) Double tiros2,
        @Min(0) @Max(100) Double tiros3,
        @Min(0) @Max(100) Double tirosLibres,
        @Min(0) @Max(100) Double tirosCampo
) {
}
