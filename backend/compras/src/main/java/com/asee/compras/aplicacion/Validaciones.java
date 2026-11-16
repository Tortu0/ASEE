package com.asee.compras.aplicacion;

import java.util.UUID;

final class Validaciones {

    private Validaciones() {}

    static void texto(String campo, String valor) {
        if (valor == null || valor.isBlank()) throw ApiException.invalida("El campo '" + campo + "' es obligatorio");
    }

    /** Los identificadores del sistema son UUID (memoria 3.3). */
    static void uuid(String campo, String valor) {
        texto(campo, valor);
        try {
            UUID.fromString(valor);
        } catch (IllegalArgumentException e) {
            throw ApiException.invalida("El campo '" + campo + "' debe ser un UUID");
        }
    }
}
