package com.asee.deportivo.dto;

import com.asee.deportivo.model.Jugador;
import com.asee.deportivo.model.Posicion;

import java.time.LocalDate;
import java.util.UUID;

public record JugadorResponse(
        UUID jugadorId,
        String nombre,
        int dorsal,
        Posicion posicion,
        Integer alturaCm,
        LocalDate fechaNacimiento,
        String fotoUrl,
        boolean activo
) {
    public static JugadorResponse from(Jugador j) {
        return new JugadorResponse(
                j.jugadorId(), j.nombre(), j.dorsal(), j.posicion(),
                j.alturaCm(), j.fechaNacimiento(), j.fotoUrl(), j.activo());
    }
}