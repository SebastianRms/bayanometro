# Falacias del Apostador y Sesgos Psicológicos en Mercados Deportivos

## 1. La Falacia de Montecarlo (Falacia del Apostador)
La Falacia del Apostador es la creencia cognitiva errónea de que si un suceso ocurre con mayor frecuencia de lo normal durante un período determinado, es menos probable que ocurra en el futuro (o viceversa), presuponiendo que los eventos futuros deben "equilibrar" el pasado.

### Principio de Independencia Estadística:
Para una secuencia de ensayos de Bernoulli independientes con probabilidad idéntica $p$:
$$P(A_n \mid A_1, A_2, \dots, A_{n-1}) = P(A_n) = p$$

Ejemplo deportivo clásico:
"El equipo X lleva 6 partidos consecutivos sin empatar; por ende, en este partido es sumamente probable el empate porque la racha ya debe romperse".
- **Desarme Matemático:** La probabilidad de que un partido termine en empate depende exclusivamente de los factores estocásticos y cuantitativos del encuentro presente ($\lambda_{\text{local}}, \lambda_{\text{visita}}$, planteamiento táctico, clima). La historia previa no tiene memoria gravitatoria sobre los dados del azar independiente.

## 2. El Desastre Matemático de la Martingala
La estrategia de Martingala consiste en duplicar la apuesta tras cada pérdida con la promesa de que la primera victoria recuperará todas las pérdidas previas más el beneficio inicial:
$$S_k = S_0 \cdot 2^{k-1}$$
Donde $S_0$ es la apuesta inicial y $k$ es el número de la iteración.

Pérdida acumulada tras $n$ derrotas consecutivas:
$$L_n = \sum_{k=1}^n S_k = S_0 (2^n - 1)$$

### Prueba de Inviabilidad Cuantitativa:
1. **Crecimiento Exponencial del Capital:**
   Si $S_0 = \$10$:
   - Derrota 1: $10 (Pérdida acumulada: $10)
   - Derrota 5: $160 (Pérdida acumulada: $310)
   - Derrota 8: $1,280 (Pérdida acumulada: $2,550)
   - Derrota 10: $5,120 (Pérdida acumulada: $10,230)
   Apostar \$5,120 para intentar recuperar únicamente \$10 de ganancia neta representa un ratio asimétrico de riesgo/beneficio catastrófico.
2. **Límites de Apuesta de las Casas (Table Limits):**
   Las casas de apuestas imponen límites máximos de apuesta por mercado, imposibilitando la continuación de la serie.
3. **Probabilidad No Nula de Rachas Adversas:**
   En una cuota 2.00 ($p = 0.5$), la probabilidad de perder 8 apuestas seguidas es:
   $$P(\text{8 derrotas}) = 0.5^8 = \frac{1}{256} \approx 0.39\%$$
   En una muestra de solo 1,000 apuestas, la probabilidad de sufrir al menos una racha de 8 derrotas consecutivas supera el 78%. El riesgo de ruina total con Martingala es del 100%.

## 3. Falacia de la Mano Caliente (Hot Hand Fallacy) y Sesgo de Confirmación
- **Hot Hand Fallacy:** Atribuir causalidad y predictibilidad a agrupamientos puramente aleatorios de aciertos. Por ejemplo, asumir que un tirador en baloncesto o un pronosticador está en "racha imparable", asignándole una probabilidad superior a su media histórica real sin evidencia matemática suficiente.
- **Sesgo de Confirmación:** Tendencia a recordar selectivamente los boletos ganadores extraordinarios (parlays de 6 piernas) mientras se minimiza, justifica u oculta el volumen masivo de boletos perdedores.
- **Criterio Bayanómetro:** Ninguna hipótesis sobre un equipo o tipster se acepta sin validación de hipótesis con un $p$-valor $< 0.05$ y un tamaño muestral de al menos 500 apuestas auditadas con seguimiento estricto de CLV.
