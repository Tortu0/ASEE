package com.asee.estadisticas.dto;

import com.asee.estadisticas.model.*;
import com.fasterxml.jackson.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Representación JSON del esquema Error del contrato. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        @NotNull Integer codigo,
        @NotNull String mensaje,
        List<@NotNull @Valid String> detalles
) {
}
