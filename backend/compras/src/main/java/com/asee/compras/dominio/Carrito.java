package com.asee.compras.dominio;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Carrito persistente de un usuario. {@code id} es el usuarioId. No reserva stock. */
public class Carrito implements Serializable {
    public String id;
    public List<ItemCarrito> items = new ArrayList<>();

    public Carrito() {}

    public Carrito(String usuarioId) {
        this.id = usuarioId;
    }
}
