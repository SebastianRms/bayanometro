package com.apuestas.exception;

public class RetiroInvalidoException extends BayanometroException {
    public RetiroInvalidoException(String motivo) {
        super("Monto de retiro inválido: " + motivo, "FIN-002");
    }
}
