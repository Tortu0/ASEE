package com.asee.compras.dominio;

import java.io.Serializable;

/** Unidades disponibles de una talla (o "unica" en productos sin tallas). También es el cuerpo JSON del contrato. */
public class StockTalla implements Serializable {
    public String talla;
    public int unidades;

    public StockTalla() {}

    public StockTalla(String talla, int unidades) {
        this.talla = talla;
        this.unidades = unidades;
    }
}
