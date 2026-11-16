package com.asee.deportivo.dto;

import java.util.List;

public record ErrorResponse(
        int codigo,
        String mensaje,
        List<String> detalles
) {
}