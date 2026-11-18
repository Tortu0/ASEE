package com.asee.deportivo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Document(collection = "partidos")
public record Partido(
        @Id UUID partidoId,
        Instant fechaHora,
        String rival,
        String lugar,
        Boolean esLocal,
        String temporada,
        String competicion,
        Integer jornada,
        EstadoPartido estado,
        Integer puntosLocal,
        Integer puntosVisitante,
        boolean ventaEntradasAbierta
) {
}