package com.asee.entradas.dto;

import com.asee.entradas.entity.EstadoAsiento;

/**
 * DTO de respuesta para un asiento del mapa.
 */
public record AsientoDto(
        String asientoId,
        String zona,
        int fila,
        int numero,
        EstadoAsiento estado
) {}
