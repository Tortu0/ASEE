package com.asee.institucional.exception;

public class PeticionInvalidaException extends RuntimeException {

    public PeticionInvalidaException(String mensaje) {
        super(mensaje);
    }
}