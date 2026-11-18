package com.asee.compras.aplicacion;

import com.asee.compras.dominio.Carrito;

public interface CarritoRepositorio {
    /** Devuelve el carrito del usuario, o uno vacío si aún no existe. */
    Carrito obtener(String usuarioId);
    void guardar(Carrito carrito);
    void vaciar(String usuarioId);
}
