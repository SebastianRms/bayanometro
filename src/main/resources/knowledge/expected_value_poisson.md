# Valor Esperado (EV) y Distribución de Poisson para Modelado Deportivo

## 1. Valor Esperado (Expected Value - EV)
El Valor Esperado es el pilar central de toda estrategia cuantitativa de apuestas. Representa la cantidad promedio que un apostador puede esperar ganar o perder por unidad apostada a largo plazo si el evento se repitiera infinitas veces bajo idénticas condiciones.

Fórmula Fundamental de EV:
$$EV = (P_{\text{ganar}} \times \text{Ganancia Neta}) - (P_{\text{perder}} \times \text{Stake})$$

Normalizado en porcentaje por unidad de apuesta:
$$EV\% = (P_{\text{real}} \times \text{Cuota Decimal}) - 1.0$$

Criterio de Inversión Cuantitativa:
- Si $EV\% > 0$: La apuesta tiene valor positivo ($EV+$). Existe una ventaja matemática (edge) sobre el precio de la casa.
- Si $EV\% = 0$: Apuesta neutral (fair odds sin comisión).
- Si $EV\% < 0$: Apuesta desaconsejada ($EV-$). El margen de la casa (vig) erosiona inexorablemente el capital a largo plazo.

## 2. Modelado de Líneas Deportivas mediante Distribución de Poisson
Los eventos de baja frecuencia e independientes por unidad de tiempo (como los goles en fútbol o los puntos en cuartos de baloncesto) pueden aproximarse mediante un proceso de Poisson:

$$P(X = k) = \frac{\lambda^k e^{-\lambda}}{k!}$$

Donde:
- $k$: Número exacto de goles/anotaciones ($k \in \{0, 1, 2, \dots\}$).
- $\lambda$: Parámetro de intensidad o valor esperado de anotaciones del equipo en el partido.
- $e$: Base del logaritmo natural ($\approx 2.71828$).

### Estimación de $\lambda$ para Fútbol:
Para un partido entre el Equipo Local ($H$) y el Equipo Visitante ($A$):
$$\lambda_{\text{local}} = \text{Ataque}_H \times \text{Defensa}_A \times \text{Promedio Goles Local Liga}$$
$$\lambda_{\text{visita}} = \text{Ataque}_A \times \text{Defensa}_H \times \text{Promedio Goles Visita Liga}$$

### Matriz Bivariada de Marcadores y Derivación de Mercados:
Asumiendo independencia condicional entre anotaciones de ambos equipos:
$$P(H = x, A = y) = P(X_H = x) \times P(Y_A = y) = \frac{\lambda_H^x e^{-\lambda_H}}{x!} \times \frac{\lambda_A^y e^{-\lambda_A}}{y!}$$

A partir de la matriz de probabilidades de marcadores exactos:
1. **Mercado 1X2 (Moneyline):**
   - $P(\text{Victoria Local}) = \sum_{x > y} P(H = x, A = y)$
   - $P(\text{Empate}) = \sum_{x = y} P(H = x, A = y)$
   - $P(\text{Victoria Visita}) = \sum_{x < y} P(H = x, A = y)$
2. **Mercado Over/Under 2.5 Goles:**
   - $P(\text{Under } 2.5) = P(0,0) + P(1,0) + P(0,1) + P(1,1) + P(2,0) + P(0,2)$
   - $P(\text{Over } 2.5) = 1.0 - P(\text{Under } 2.5)$
3. **Ambos Equipos Anotan (BTTS Yes):**
   - $P(\text{BTTS Yes}) = (1 - e^{-\lambda_H}) \times (1 - e^{-\lambda_A})$

Cualquier discrepancia donde $P_{\text{Poisson}} \times \text{Cuota} - 1 > 0$ se etiqueta como una oportunidad $EV+$.
