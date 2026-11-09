package com.asee.compras.dto;

import java.util.List;

public record PedidoRequest(
        String usuarioId,
        String direccionEnvio,
        List<LineaRequest> lineas) {

    public record LineaRequest(String productoId, String talla, int cantidad) {
    }
}