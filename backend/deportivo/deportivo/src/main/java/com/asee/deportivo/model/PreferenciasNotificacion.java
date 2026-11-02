package com.asee.deportivo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.UUID;

@Document(collection = "preferencias_notificacion")
public record PreferenciasNotificacion(
        @Id UUID usuarioId,
        List<UUID> jugadoresFavoritos,
        List<CategoriaNoticia> categoriasFavoritas,
        boolean avisarInicioPartido
) {
}