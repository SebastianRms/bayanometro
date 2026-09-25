# Dutching, Hedging y Trampas de Margen (Vig Traps)

## 1. Dutching Cuantitativo (Repartición de Stakes Multiselección)
El Dutching consiste en distribuir una inversión total ($S_{\text{total}}$) entre $n$ selecciones mutuamente excluyentes dentro de un mercado para obtener exactamente el mismo beneficio neto, sin importar cuál de las selecciones seleccionadas resulte ganadora.

### Fórmulas Matemáticas de Dutching:
Para $n$ selecciones con cuotas decimales $O_1, O_2, \dots, O_n$:
1. Probabilidad implícita combinada de las selecciones:
   $$\Pi = \sum_{i=1}^n \frac{1}{O_i}$$
2. Asignación del stake a cada selección individual $k$:
   $$\text{Stake}_k = S_{\text{total}} \times \frac{\frac{1}{O_k}}{\Pi} = S_{\text{total}} \times \frac{\frac{1}{O_k}}{\sum_{j=1}^n \frac{1}{O_j}}$$
3. Retorno Bruto común en cualquier selección ganadora:
   $$R_{\text{bruto}} = \text{Stake}_k \times O_k = \frac{S_{\text{total}}}{\Pi}$$
4. Retorno Neto (Ganancia o Pérdida):
   $$G_{\text{neta}} = R_{\text{bruto}} - S_{\text{total}} = S_{\text{total}} \left( \frac{1}{\Pi} - 1 \right)$$

### La Trampa del Dutching en la Misma Casa de Apuestas (Vig Trap):
Si el usuario intenta hacer Dutching en la misma casa cubriendo todas o la mayoría de las opciones (ejemplo: apostar a Local y a Empate simultáneamente en un partido de fútbol):
- En un mercado 1X2 completo de una sola casa, la suma de inversas $\sum_{i=1}^3 \frac{1}{O_i} = 1 + \text{Vig} \approx 1.05 \text{ a } 1.10$.
- Cubrir 2 de 3 opciones con Dutching paga doble comisión implícita si el evento no considerado se materializa. Y si se cubren todas las opciones, la pérdida neta es matemáticamente garantizada: $G_{\text{neta}} = S_{\text{total}} (\frac{1}{1.08} - 1) = -7.4\%$.
- Conclusión analítica: El Dutching solo genera ganancia sin riesgo si se realiza entre diferentes casas de apuestas donde exista arbitraje puro ($\Pi < 1.0$). En la misma casa, casi siempre destruye valor ($EV-$).

## 2. Hedging (Cobertura Dinámica de Apuestas)
El Hedging consiste en colocar una apuesta compensatoria en el desenlace opuesto a una posición previa para asegurar ganancias o mitigar pérdidas potenciales.

### Fórmula de Hedging para Bloqueo de Ganancia Uniforme:
Si se tiene una apuesta original con Stake $S_1$ a cuota $O_1$ (Retorno potencial $R_1 = S_1 \times O_1$), y la cuota de la contra-opción es $O_2$:
$$\text{Stake}_{\text{contra}} = \frac{R_1}{O_2} = \frac{S_1 \times O_1}{O_2}$$

Beneficio garantizado libre de riesgo en cualquier desenlace:
$$G_{\text{hedge}} = R_1 - S_1 - \text{Stake}_{\text{contra}} = S_1 \left( O_1 - 1 - \frac{O_1}{O_2} \right)$$

Condición matemática para beneficio garantizado positivo ($G_{\text{hedge}} > 0$):
$$\frac{1}{O_1} + \frac{1}{O_2} < 1.0$$

### Crítica Cuantitativa del Hedging:
Aunque el hedging elimina la varianza a corto plazo, si la apuesta original tenía un Valor Esperado positivo ($EV+$) y el mercado de contra-apuesta contiene el margen habitual del bookmaker (vig), realizar hedging de forma recurrente:
1. Paga una segunda comisión a la casa de apuestas.
2. Reduce sistemáticamente el valor esperado acumulado a largo plazo.
3. Solo se justifica cuantitativamente por restricciones severas de aversión al riesgo o ante la necesidad de evitar el riesgo de ruina cuando el retorno potencial representa un porcentaje masivo del bankroll total.
