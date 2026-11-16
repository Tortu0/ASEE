package com.asee.entradas.entity;

public enum EstadoReserva {
    activa,
    /** Compra en curso: la reserva está "reclamada" por una petición y nadie más puede tocarla. */
    pagando,
    pagada,
    cancelada,
    expirada
}