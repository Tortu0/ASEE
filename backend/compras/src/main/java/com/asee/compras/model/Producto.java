package com.asee.compras.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Document(collection = "productos")
public class Producto {

    @Id
    private String id;

    private String nombre;
    private String descripcion;
    private BigDecimal precio;

    // URLs de las fotos (se rellenará con Firebase más adelante)
    private List<String> fotos = new ArrayList<>();

    // Stock por talla, por ejemplo {"S": 10, "M": 5, "L": 0}
    private Map<String, Integer> stockPorTalla = new HashMap<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public void setPrecio(BigDecimal precio) {
        this.precio = precio;
    }

    public List<String> getFotos() {
        return fotos;
    }

    public void setFotos(List<String> fotos) {
        this.fotos = fotos;
    }

    public Map<String, Integer> getStockPorTalla() {
        return stockPorTalla;
    }

    public void setStockPorTalla(Map<String, Integer> stockPorTalla) {
        this.stockPorTalla = stockPorTalla;
    }
}