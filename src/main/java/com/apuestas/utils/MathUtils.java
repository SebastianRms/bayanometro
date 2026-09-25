package com.apuestas.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class MathUtils {
    
    private MathUtils() {
        // Utility class
    }

    /**
     * Calcula la fracción óptima de Kelly: f* = (bp - q) / b
     * donde:
     *   b = cuota decimal - 1 (ganancia neta por peso apostado)
     *   p = probabilidad real estimada (entre 0 y 1)
     *   q = 1 - p (probabilidad de perder)
     *
     * @param cuota Cuota decimal ofrecida por la casa (debe ser > 1.0)
     * @param probabilidadReal Probabilidad real estimada (debe estar en (0.0, 1.0])
     * @return Fracción del bankroll a apostar, o BigDecimal.ZERO si no hay valor o los parámetros son inválidos.
     */
    public static BigDecimal calcularKellyCriterion(BigDecimal cuota, BigDecimal probabilidadReal) {
        if (cuota == null || probabilidadReal == null) {
            return BigDecimal.ZERO;
        }

        // Una cuota decimal válida en apuestas debe ser mayor a 1.0 (ganancia > 0)
        if (cuota.compareTo(BigDecimal.ONE) <= 0) {
            return BigDecimal.ZERO;
        }

        // La probabilidad debe ser positiva y menor o igual al 100% (1.0)
        if (probabilidadReal.compareTo(BigDecimal.ZERO) <= 0 || probabilidadReal.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal b = cuota.subtract(BigDecimal.ONE);
        BigDecimal p = probabilidadReal;
        BigDecimal q = BigDecimal.ONE.subtract(p);
        
        BigDecimal dividendo = (b.multiply(p)).subtract(q);
        if (dividendo.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        
        return dividendo.divide(b, 4, RoundingMode.HALF_UP);
    }

    /**
     * Redondea un valor monetario a 2 decimales usando HALF_UP.
     * Retorna BigDecimal.ZERO si el valor de entrada es null.
     */
    public static BigDecimal redondearBancario(BigDecimal valor) {
        if (valor == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
