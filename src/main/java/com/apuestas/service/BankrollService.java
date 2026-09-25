package com.apuestas.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.apuestas.entity.BalanceActual;

public interface BankrollService {
    String registrarApuestaDirecta(Long userId, Long eventoId, String seleccion, BigDecimal cuotaReal, BigDecimal monto);
    String registrarParlay(Long userId, List<Map<String, Object>> piernas, BigDecimal monto);
    String procesarRetiro(Long userId, BigDecimal monto, String concepto);
    String registrarDeposito(BigDecimal monto, String concepto);
    BalanceActual obtenerResumenActual(Long userId);
    List<Map<String, Object>> obtenerHistorialApuestas(Long userId, int limite);
    Map<String, Object> conciliarResultadosYActualizarBank(Long userId);
    String liquidarApuestaManual(Long userId, Long boletoId, String resultado);
}
