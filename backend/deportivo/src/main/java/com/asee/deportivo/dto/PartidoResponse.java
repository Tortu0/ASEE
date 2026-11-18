package com.asee.deportivo.dto;

import com.asee.deportivo.model.EstadoPartido;
import com.asee.deportivo.model.Partido;

import java.time.Instant;
import java.util.UUID;

public record PartidoResponse(
        UUID partidoId,
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
    public static PartidoResponse from(Partido p) {
        return new PartidoResponse(
                p.partidoId(), p.fechaHora(), p.rival(), p.lugar(), p.esLocal(),
                p.temporada(), p.competicion(), p.jornada(), p.estado(),
                p.puntosLocal(), p.puntosVisitante(), p.ventaEntradasAbierta());
    }
}