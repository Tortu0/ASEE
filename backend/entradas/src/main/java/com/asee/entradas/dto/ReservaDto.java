package com.asee.entradas.dto;

import java.time.Instant;
import java.util.List;

/**
 * DTO de respuesta para una reserva.
 */
public record ReservaDto(
        String reservaId,
        String partidoId,
        String usuarioId,
        List<AsientoDto> asientos,
        Instant expiraEn
) {}
