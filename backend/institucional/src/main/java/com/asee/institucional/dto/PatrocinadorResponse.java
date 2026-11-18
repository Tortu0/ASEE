package com.asee.institucional.dto;

import com.asee.institucional.model.NivelPatrocinio;
import com.asee.institucional.model.Patrocinador;

import java.util.UUID;

public record PatrocinadorResponse(
        UUID patrocinadorId,
        String nombre,
        String logoUrl,
        String enlace,
        NivelPatrocinio nivel
) {
    public static PatrocinadorResponse from(Patrocinador p) {
        return new PatrocinadorResponse(
                p.patrocinadorId(), p.nombre(), p.logoUrl(), p.enlace(), p.nivel());
    }
}