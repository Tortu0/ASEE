package com.asee.entradas.dto;

import java.util.List;

/**
 * DTO de respuesta para errores de la API.
 */
public record ErrorDto(
        int codigo,
        String mensaje,
        List<String> detalles
) {}
