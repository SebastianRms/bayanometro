package com.apuestas.exception;

import java.math.BigDecimal;

public class SaldoInsuficienteException extends BayanometroException {
    public SaldoInsuficienteException(BigDecimal saldoActual, BigDecimal montoRequerido) {
        super(String.format("Saldo insuficiente. Tienes $%.2f MXN e intentaste usar $%.2f MXN.", 
              saldoActual, montoRequerido), "FIN-001");
    }
}
