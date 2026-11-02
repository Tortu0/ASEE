package com.asee.deportivo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Document(collection = "noticias")
public record Noticia(
        @Id UUID noticiaId,
        String titulo,
        String contenido,
        CategoriaNoticia categoria,
        List<UUID> jugadoresRelacionados,
        String imagenUrl,
        UUID autorId,
        Instant fechaPublicacion
) {
}