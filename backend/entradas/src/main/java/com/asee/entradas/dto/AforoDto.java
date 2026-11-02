package com.asee.entradas.dto;

/**
 * DTO de respuesta para el aforo de un partido.
 */
public record AforoDto(
        String partidoId,
        int capacidadTotal,
        int ocupados,
        int reservados,
        int libres
) {}
