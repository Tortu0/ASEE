package com.asee.compras.aplicacion;

import com.asee.compras.dominio.Producto;

import java.util.List;
import java.util.Optional;

public interface ProductoRepositorio {
    List<Producto> listar(String categoria, String jugadorId, String patrocinadorId, int pagina, int tamano);
    Optional<Producto> porId(String id);
    Producto guardar(Producto producto);
    boolean eliminar(String id);

    /** Descuento ATÓMICO: devuelve false (sin cambiar nada) si la talla no existe o no hay unidades suficientes. */
    boolean descontarStock(String productoId, String talla, int unidades);

    void reponerStock(String productoId, String talla, int unidades);
}
