package com.asee.compras.dominio;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Producto del catálogo. {@code id} es el productoId (UUID en texto) y se guarda como _id en MongoDB. */
public class Producto implements Serializable {
    public String id;
    public String nombre;
    public String descripcion;
    public String categoria;
    public BigDecimal precio;
    public List<StockTalla> stock = new ArrayList<>();
    public List<String> fotos = new ArrayList<>();
    public String jugadorId;
    public String patrocinadorId;
}
