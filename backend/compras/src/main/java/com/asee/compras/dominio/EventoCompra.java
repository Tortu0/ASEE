package com.asee.compras.dominio;

import java.math.BigDecimal;

/**
 * Cuerpo del evento (memoria 3.4): eventoId, usuarioId, referenciaId, fecha (date-time), importe y descripcion.
 * El tipo ("pedido creado") viaja en la routing key, no en el cuerpo.
 */
public record EventoCompra(String eventoId, String usuarioId, String referenciaId,
                           String fecha, BigDecimal importe, String descripcion) {
}
