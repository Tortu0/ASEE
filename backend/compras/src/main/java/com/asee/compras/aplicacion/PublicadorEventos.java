package com.asee.compras.aplicacion;

import com.asee.compras.dominio.EventoCompra;

public interface PublicadorEventos {
    /** Publica en el broker; lanza una excepción no comprobada si no se pudo entregar. */
    void publicar(String routingKey, EventoCompra evento);
}
