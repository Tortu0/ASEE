package com.asee.deportivo.dto;

import com.asee.deportivo.model.CategoriaNoticia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record NoticiaCreate(
        @NotBlank String titulo,
        @NotBlank String contenido,
        @NotNull CategoriaNoticia categoria,
        List<UUID> jugadoresRelacionados,
        String imagenUrl
) {
}