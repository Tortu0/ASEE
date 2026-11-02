package com.asee.deportivo.dto;

import com.asee.deportivo.model.CategoriaNoticia;
import com.asee.deportivo.model.Noticia;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NoticiaResponse(
        UUID noticiaId,
        String titulo,
        String contenido,
        CategoriaNoticia categoria,
        List<UUID> jugadoresRelacionados,
        String imagenUrl,
        UUID autorId,
        Instant fechaPublicacion
) {
    public static NoticiaResponse from(Noticia n) {
        return new NoticiaResponse(
                n.noticiaId(), n.titulo(), n.contenido(), n.categoria(),
                n.jugadoresRelacionados(), n.imagenUrl(), n.autorId(), n.fechaPublicacion());
    }
}