package com.asee.deportivo.dto;

import com.asee.deportivo.model.CategoriaNoticia;
import com.asee.deportivo.model.PreferenciasNotificacion;

import java.util.List;
import java.util.UUID;

public record PreferenciasDto(
        UUID usuarioId,
        List<UUID> jugadoresFavoritos,
        List<CategoriaNoticia> categoriasFavoritas,
        Boolean avisarInicioPartido
) {
    public static PreferenciasDto from(PreferenciasNotificacion p) {
        return new PreferenciasDto(
                p.usuarioId(), p.jugadoresFavoritos(),
                p.categoriasFavoritas(), p.avisarInicioPartido());
    }
}