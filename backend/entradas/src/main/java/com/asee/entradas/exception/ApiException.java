package com.asee.entradas.exception;

import org.springframework.http.HttpStatus;

/**
 * Excepción de dominio que mapea directamente a una respuesta HTTP con código de estado.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String mensaje) {
        super(mensaje);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
