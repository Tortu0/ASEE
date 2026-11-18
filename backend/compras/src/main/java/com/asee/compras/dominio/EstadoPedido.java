package com.asee.compras.dominio;

/** Estados del contrato (esquema EstadoPedido). {@link #valor()} es el texto que viaja por la API. */
public enum EstadoPedido {
    PENDIENTE("pendiente"), PAGADO("pagado"), PREPARANDO("preparando"),
    ENVIADO("enviado"), ENTREGADO("entregado"), CANCELADO("cancelado");

    private final String valor;

    EstadoPedido(String valor) {
        this.valor = valor;
    }

    public String valor() {
        return valor;
    }

    /** null si el texto no corresponde a ningún estado. */
    public static EstadoPedido deValor(String texto) {
        if (texto == null) return null;
        for (EstadoPedido e : values()) {
            if (e.valor.equals(texto)) return e;
        }
        return null;
    }
}
