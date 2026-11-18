package com.asee.compras.dominio;

/**
 * Transiciones que puede pedir staff/admin con PUT /pedidos/{id}/estado:
 * pagado -> preparando -> enviado -> entregado, o repetir el estado actual.
 * pendiente -> pagado lo hace el proceso de pago y cancelar se hace con DELETE.
 */
public final class FlujoEstados {

    private FlujoEstados() {}

    public static boolean permitido(EstadoPedido desde, EstadoPedido hasta) {
        if (desde == hasta) return true;
        return switch (desde) {
            case PAGADO -> hasta == EstadoPedido.PREPARANDO;
            case PREPARANDO -> hasta == EstadoPedido.ENVIADO;
            case ENVIADO -> hasta == EstadoPedido.ENTREGADO;
            default -> false;
        };
    }
}
