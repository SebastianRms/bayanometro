# Criterio de Kelly y Gestión Cuantitativa de Bankroll

## 1. Fundamento Matemático del Criterio de Kelly
El Criterio de Kelly es una fórmula matemática diseñada para determinar el tamaño óptimo de una serie de apuestas con el fin de maximizar la tasa de crecimiento logarítmico del capital a largo plazo, minimizando simultáneamente la probabilidad de ruina.

Fórmula General:
$$f^* = \frac{b \cdot p - q}{b} = \frac{p \cdot (b + 1) - 1}{b} = p - \frac{q}{b}$$

Donde:
- $f^*$: Fracción del bankroll actual a apostar ($0 \le f^* \le 1$).
- $b$: Cuota neta en formato decimal ($b = \text{cuota decimal} - 1$). Por ejemplo, para una cuota 2.50, $b = 1.50$.
- $p$: Probabilidad real estimada del evento ($0 < p < 1$).
- $q$: Probabilidad de fracaso ($q = 1 - p$).

Regla de corte estricta:
Si $b \cdot p - q \le 0$, entonces $f^* \le 0$. Esto implica que la apuesta tiene Valor Esperado negativo ($EV \le 0$). En este escenario, NUNCA se debe apostar.

## 2. Full Kelly vs. Fractional Kelly (Mitigación del Riesgo de Ruina)
- **Full Kelly ($1.0 \times f^*$):**
  Maximiza teóricamente el valor esperado del logaritmo del capital $E[\ln(X_n)]$. Sin embargo, en la práctica presenta serios inconvenientes:
  1. Alta volatilidad: Drawdowns superiores al 50% ocurren con más del 33% de probabilidad en series largas.
  2. Sensibilidad al error de estimación de $p$: Si la estimación subjetiva de la probabilidad sobrestima la probabilidad real en un pequeño margen, Full Kelly sobreapuesta agresivamente, llevando el crecimiento a zona negativa (sobre-Kelly).
  3. Estrés psicológico y tilt para el operador.

- **Fractional Kelly (Half Kelly $0.5 \times f^*$, Quarter Kelly $0.25 \times f^*$):**
  - **Half Kelly ($f^* / 2$):** Proporciona el 75% del crecimiento máximo teórico de Full Kelly, pero reduce la varianza y la volatilidad del bankroll en un 50%. El riesgo de caer en sobre-Kelly por error de calibración se reduce drásticamente.
  - **Quarter Kelly ($f^* / 4$):** Reduce la volatilidad en un 75% y es la convención institucional y profesional recomendada para trading deportivo y mercados de alta incertidumbre.

## 3. Dinámica del Bankroll en Bayanómetro
- **Colchón de Seguridad:** Reserva obligatoria del 15% del bankroll disponible para absorber la varianza inherente.
- **Piso Crítico:** Si el saldo cae por debajo de $10.00 USD, se congelan apuestas complejas o de alto riesgo y se priorizan selecciones conservadoras $EV+$ de stake mínimo.
- **Ajuste Dinámico de Stakes:** El monto apostado debe recalcularse en cada iteración en función del capital líquido real disponible, nunca sobre el capital inicial.
