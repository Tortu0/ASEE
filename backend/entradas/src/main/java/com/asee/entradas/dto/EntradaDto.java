package com.asee.entradas.dto;

import com.asee.entradas.entity.EstadoEntrada;
import com.asee.entradas.entity.MetodoPago;
import com.asee.entradas.entity.TipoEntrada;

import java.time.Instant;

/**
 * DTO de respuesta para una entrada.
 */
public record EntradaDto(
        String entradaId,
        String partidoId,
        String usuarioId,
        AsientoDto asiento,
        TipoEntrada tipoEntrada,
        double precioPagado,
        double descuentoAplicado,
        MetodoPago metodoPago,
        String codigoQr,
        EstadoEntrada estado,
        Instant fechaCompra
) {}
