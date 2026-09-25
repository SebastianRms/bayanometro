package com.apuestas.exception;

public class ApiExternaException extends BayanometroException {
    public ApiExternaException(String servicio, String detalle) {
        super(String.format("Fallo al conectar con %s: %s", servicio, detalle), "EXT-001");
    }
}
