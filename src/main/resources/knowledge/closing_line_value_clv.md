# Closing Line Value (CLV) y Remoción de Vig (Fair Odds)

## 1. Closing Line Value (CLV - Valor Frente a la Línea de Cierre)
La cuota de cierre (Closing Line) representa el consenso final de información de todo el mercado deportivo justo antes del inicio del evento. Los apostadores profesionales, sindicatos cuantitativos y creadores de mercado inyectan millones de dólares en volumen, transformando la línea de cierre en el estimador insesgado más eficiente de las probabilidades verdaderas.

### Definición y Cálculo de CLV:
Si colocas una apuesta a una cuota $O_{\text{apostada}}$ y la cuota de cierre final sin margen (fair closing line) es $O_{\text{cierre, fair}}$:
$$CLV\% = \left( \frac{O_{\text{apostada}}}{O_{\text{cierre, fair}}} - 1 \right) \times 100$$

Aproximación simplificada contra la cuota de cierre con vig:
$$CLV_{\text{crudo}}\% = \left( \frac{O_{\text{apostada}}}{O_{\text{cierre}}} - 1 \right) \times 100$$

### Importancia Estadística del CLV:
- **Separación de Habilidad vs. Suerte:** Los resultados de apuestas a corto plazo (ganar o perder en 50 o 100 apuestas) están dominados por la varianza estocástica. Sin embargo, batir consistentemente la cuota de cierre ($CLV\% > 0$) en una muestra representativa ($N > 300$) tiene una correlación de casi 1.0 con la rentabilidad matemática a largo plazo.
- Si un apostador gana dinero pero sus apuestas tienen $CLV\% \le 0$, se encuentra en una racha de varianza positiva (suerte) y su bankroll eventualmente convergerá a pérdidas conforme aumente la muestra de ensayos.

## 2. Remoción de Vig / Overround (Extracción de Cuotas Justas)
Las casas de apuestas agregan un sobreprecio o comisión implícita (Overround o Vig) en sus cuotas para garantizar su rentabilidad independientemente del desenlace del evento.

### Cálculo del Overround de Mercado:
Para un mercado de $k$ resultados disjuntos con cuotas $O_1, O_2, \dots, O_k$:
$$S_{\text{mercado}} = \sum_{i=1}^k \frac{1}{O_i}$$
$$\text{Overround (Vig)} = S_{\text{mercado}} - 1.0 = \left( \sum_{i=1}^k \frac{1}{O_i} \right) - 1.0$$
$$\text{Margen en Porcentaje} = \left( 1 - \frac{1}{S_{\text{mercado}}} \right) \times 100$$

### Métodos para Obtener la Probabilidad Justa (Fair Probability) y Cuota Justa (Fair Odds):
1. **Método Multiplicativo Proporcional (Estándar):**
   Asume que el vig se distribuye de manera estrictamente proporcional a la probabilidad implícita de cada selección:
   $$P_{\text{fair}, i} = \frac{\frac{1}{O_i}}{\sum_{j=1}^k \frac{1}{O_j}} = \frac{P_{\text{implícita}, i}}{S_{\text{mercado}}}$$
   Cuota Justa Desmargenada:
   $$O_{\text{fair}, i} = \frac{1}{P_{\text{fair}, i}} = O_i \times S_{\text{mercado}}$$

2. **Sesgo Favorito-Longshot (Favorite-Longshot Bias / Modelo de Shin):**
   En mercados reales con apostadores recreativos, las casas de apuestas cargan un porcentaje desproporcionadamente más alto de vig sobre los "longshots" (selecciones con cuotas muy altas y baja probabilidad) que sobre los favoritos.
   En consecuencia, las selecciones con cuotas extremadamente altas suelen tener un $EV-$ mucho más severo que las selecciones favoritas.

Toda cuota apostada $O_{\text{apostada}} > O_{\text{fair}}$ representa una ineficiencia explotable con Valor Esperado positivo ($EV+$).
