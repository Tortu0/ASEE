package com.asee.entradas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * DTO de petición para crear una reserva.
 */
public record ReservaCreate(
        @NotBlank String partidoId,
        @NotEmpty @Size(min = 1, max = 10) List<String> asientos
) {}
