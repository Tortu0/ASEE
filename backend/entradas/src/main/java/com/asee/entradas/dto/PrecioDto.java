package com.asee.entradas.dto;

import com.asee.entradas.entity.TipoEntrada;

/**
 * DTO de respuesta para una tarifa/precio de un partido.
 */
public record PrecioDto(
        String zona,
        TipoEntrada tipoEntrada,
        double precio
) {}
