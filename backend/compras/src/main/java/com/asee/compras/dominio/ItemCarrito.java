package com.asee.compras.dominio;

import java.io.Serializable;
import java.math.BigDecimal;

public class ItemCarrito implements Serializable {
    public String itemId;
    public String productoId;
    public String nombre;
    public String talla;
    public int cantidad;
    public BigDecimal precioUnitario;

    public ItemCarrito() {}

    public ItemCarrito copia() {
        ItemCarrito c = new ItemCarrito();
        c.itemId = itemId;
        c.productoId = productoId;
        c.nombre = nombre;
        c.talla = talla;
        c.cantidad = cantidad;
        c.precioUnitario = precioUnitario;
        return c;
    }
}
