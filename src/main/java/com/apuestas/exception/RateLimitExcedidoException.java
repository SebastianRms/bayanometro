package com.apuestas.exception;

import java.time.LocalDateTime;

public class RateLimitExcedidoException extends BayanometroException {
    public RateLimitExcedidoException(LocalDateTime reintentoPermitido) {
        super("Límite de 30 consultas alcanzado. Próxima ventana disponible: " + reintentoPermitido, "SEC-002");
    }
}
