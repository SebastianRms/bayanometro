package com.apuestas.exception;

public abstract class BayanometroException extends RuntimeException {
    private final String codigoError;

    public BayanometroException(String mensaje, String codigoError) {
        super(mensaje);
        this.codigoError = codigoError;
    }

    public String getCodigoError() {
        return codigoError;
    }
}
