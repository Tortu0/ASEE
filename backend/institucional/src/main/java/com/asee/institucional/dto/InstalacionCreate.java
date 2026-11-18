package com.asee.institucional.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record InstalacionCreate(
        @NotBlank String nombre,
        @NotBlank String direccion,
        String comoLlegar,
        Integer capacidad,
        List<String> fotos,
        Double latitud,
        Double longitud
) {
}