package com.asee.institucional.dto;

import com.asee.institucional.model.Instalacion;

import java.util.List;
import java.util.UUID;

public record InstalacionResponse(
        UUID instalacionId,
        String nombre,
        String direccion,
        String comoLlegar,
        Integer capacidad,
        List<String> fotos,
        Double latitud,
        Double longitud
) {
    public static InstalacionResponse from(Instalacion i) {
        return new InstalacionResponse(
                i.instalacionId(), i.nombre(), i.direccion(), i.comoLlegar(),
                i.capacidad(), i.fotos(), i.latitud(), i.longitud());
    }
}