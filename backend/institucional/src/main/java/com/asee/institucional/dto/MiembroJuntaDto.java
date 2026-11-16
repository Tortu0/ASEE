package com.asee.institucional.dto;

import com.asee.institucional.model.MiembroJunta;

// Sin anotaciones de validación a propósito: el PUT de la junta recibe un
// array JSON y Spring no valida de forma fiable los elementos de una lista
// en el cuerpo. La comprobación de nombre y cargo se hace en el servicio.
public record MiembroJuntaDto(
        String nombre,
        String cargo,
        String fotoUrl
) {
    public static MiembroJuntaDto from(MiembroJunta m) {
        return new MiembroJuntaDto(m.nombre(), m.cargo(), m.fotoUrl());
    }
}