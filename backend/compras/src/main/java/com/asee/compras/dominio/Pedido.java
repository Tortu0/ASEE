package com.asee.compras.dominio;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Pedido. {@code id} se deriva de (usuarioId, Idempotency-Key): es estable ante reintentos y garantiza
 * por sí mismo que no se crean dos pedidos con la misma clave (clave primaria única de MongoDB).
 */
public class Pedido implements Serializable {
    public String id;
    public String usuarioId;
    public String huella;                 // resumen de los datos de la petición (para detectar claves reutilizadas)
    public List<ItemCarrito> items = new ArrayList<>();
    public BigDecimal subtotal;
    public BigDecimal descuentoSocio;
    public BigDecimal total;
    public String direccionEnvio;
    public String metodoPago;
    public EstadoPedido estado;
    public Instant fechaPedido;

    // Cobro (memoria 3.6): referencia estable ante reintentos y estado del pago
    public String referenciaCobro;
    public String pagoEstado;             // en_curso | incierto | completado
    public boolean stockDescontado;
    public boolean stockRepuesto;
    public String reembolsoEstado;        // null | pendiente | completado

    // Evento "pedido creado" (memoria 3.4): se guarda con el pedido y un publicador reintenta el envío
    public String eventoId;
    public boolean eventoPublicado;
}
