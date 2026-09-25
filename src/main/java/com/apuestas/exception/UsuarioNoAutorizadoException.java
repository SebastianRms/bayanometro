package com.apuestas.exception;

public class UsuarioNoAutorizadoException extends BayanometroException {
    public UsuarioNoAutorizadoException(Long telegramId) {
        super("Acceso denegado. Telegram ID no autorizado: " + telegramId, "SEC-001");
    }
}
