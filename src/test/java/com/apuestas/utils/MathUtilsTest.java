package com.apuestas.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MathUtilsTest {

    @Test
    @DisplayName("Debe calcular correctamente la fracción de Kelly cuando existe valor esperado positivo (+EV)")
    void testKellyConValorPositivo() {
        // Cuota decimal 2.0 (ganancia b = 1.0) con probabilidad estimada del 60% (p = 0.60, q = 0.40)
        // Kelly = (1.0 * 0.60 - 0.40) / 1.0 = 0.2000 (20% del bankroll)
        BigDecimal cuota = new BigDecimal("2.00");
        BigDecimal prob = new BigDecimal("0.60");

        BigDecimal resultado = MathUtils.calcularKellyCriterion(cuota, prob);

        assertNotNull(resultado);
        assertEquals(new BigDecimal("0.2000"), resultado);
    }

    @Test
    @DisplayName("Debe retornar cero cuando la apuesta no tiene valor esperado positivo (-EV)")
    void testKellySinValorRetornaCero() {
        // Cuota 2.0 pero con solo 40% de probabilidad (apuesta desfavorecedora)
        BigDecimal cuota = new BigDecimal("2.00");
        BigDecimal prob = new BigDecimal("0.40");

        BigDecimal resultado = MathUtils.calcularKellyCriterion(cuota, prob);

        assertEquals(BigDecimal.ZERO, resultado);
    }

    @ParameterizedTest
    @CsvSource({
        "1.00, 0.50",
        "0.90, 0.80",
        "-1.50, 0.50"
    })
    @DisplayName("Debe retornar cero si la cuota es menor o igual a 1.0 (cuota inválida en apuestas)")
    void testKellyCuotaInvalida(String cuotaStr, String probStr) {
        BigDecimal cuota = new BigDecimal(cuotaStr);
        BigDecimal prob = new BigDecimal(probStr);

        BigDecimal resultado = MathUtils.calcularKellyCriterion(cuota, prob);

        assertEquals(BigDecimal.ZERO, resultado);
    }

    @ParameterizedTest
    @CsvSource({
        "2.50, 0.00",
        "2.50, -0.10",
        "2.50, 1.05"
    })
    @DisplayName("Debe retornar cero si la probabilidad real está fuera del rango válido (0.0, 1.0]")
    void testKellyProbabilidadFueraDeRango(String cuotaStr, String probStr) {
        BigDecimal cuota = new BigDecimal(cuotaStr);
        BigDecimal prob = new BigDecimal(probStr);

        BigDecimal resultado = MathUtils.calcularKellyCriterion(cuota, prob);

        assertEquals(BigDecimal.ZERO, resultado);
    }

    @Test
    @DisplayName("Debe proteger contra NullPointerException y retornar cero si los argumentos son nulos")
    void testKellyConParametrosNulos() {
        assertEquals(BigDecimal.ZERO, MathUtils.calcularKellyCriterion(null, new BigDecimal("0.50")));
        assertEquals(BigDecimal.ZERO, MathUtils.calcularKellyCriterion(new BigDecimal("2.00"), null));
        assertEquals(BigDecimal.ZERO, MathUtils.calcularKellyCriterion(null, null));
    }

    @Test
    @DisplayName("Debe redondear valores monetarios a 2 decimales usando HALF_UP")
    void testRedondearBancario() {
        BigDecimal entrada = new BigDecimal("123.456");
        BigDecimal resultado = MathUtils.redondearBancario(entrada);

        assertEquals(new BigDecimal("123.46"), resultado);
    }

    @Test
    @DisplayName("Debe retornar 0.00 cuando se pasa un valor nulo a redondearBancario")
    void testRedondearBancarioNull() {
        BigDecimal resultado = MathUtils.redondearBancario(null);

        assertNotNull(resultado);
        assertEquals(new BigDecimal("0.00"), resultado);
    }
}
