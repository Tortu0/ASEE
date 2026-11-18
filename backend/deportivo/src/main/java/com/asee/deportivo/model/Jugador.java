package com.asee.deportivo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.UUID;

@Document(collection = "jugadores")
public record Jugador(
        @Id UUID jugadorId,
        String nombre,
        int dorsal,
        Posicion posicion,
        Integer alturaCm,
        LocalDate fechaNacimiento,
        String fotoUrl,
        boolean activo
) {
}