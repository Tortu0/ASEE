package com.asee.entradas.dto;

import com.asee.entradas.entity.MetodoPago;
import com.asee.entradas.entity.TipoEntrada;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO de petición para comprar entradas a partir de una reserva.
 */
public record CompraEntradas(
        @NotBlank String reservaId,
        @NotNull TipoEntrada tipoEntrada,
        @NotNull MetodoPago metodoPago
) {}
