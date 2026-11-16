package com.asee.deportivo.exception;

public class PeticionInvalidaException extends RuntimeException {

    public PeticionInvalidaException(String mensaje) {
        super(mensaje);
    }
}