package com.asee.institucional.dto;

import com.asee.institucional.model.Titulo;

import java.util.UUID;

public record TituloResponse(
        UUID tituloId,
        String competicion,
        String temporada,
        String descripcion,
        String fotoUrl
) {
    public static TituloResponse from(Titulo t) {
        return new TituloResponse(
                t.tituloId(), t.competicion(), t.temporada(), t.descripcion(), t.fotoUrl());
    }
}