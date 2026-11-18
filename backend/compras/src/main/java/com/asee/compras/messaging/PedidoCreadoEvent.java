package com.asee.compras.messaging;

import com.asee.compras.model.Pedido;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PedidoCreadoEvent(
        String pedidoId,
        String usuarioId,
        List<Linea> lineas,
        BigDecimal total,
        Instant fecha) {

    public record Linea(String productoId, String talla, int cantidad, BigDecimal precioUnitario) {
    }

    public static PedidoCreadoEvent from(Pedido pedido) {
        List<Linea> lineas = pedido.getLineas().stream()
                .map(l -> new Linea(l.getProductoId(), l.getTalla(), l.getCantidad(), l.getPrecioUnitario()))
                .toList();
        return new PedidoCreadoEvent(pedido.getId(), pedido.getUsuarioId(), lineas,
                pedido.getTotal(), pedido.getFecha());
    }
}