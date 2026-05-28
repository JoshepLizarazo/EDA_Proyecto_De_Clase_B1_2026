# Modelo Matemático y Epidemiológico

## El problema real

Las epidemias no se propagan de manera uniforme — su velocidad y alcance dependen directamente de cómo están conectadas las personas entre sí. Una misma enfermedad con la misma probabilidad de contagio puede generar un brote devastador en una red densamente conectada, o extinguirse rápidamente en una red fragmentada.

El objetivo matemático es modelar este fenómeno usando teoría de grafos y simular distintas intervenciones (vacunación) para evaluar cuál detiene el brote más eficazmente bajo restricción de recursos (solo el 20% de la población puede vacunarse).

---

## 1. Representación matemática — Teoría de Grafos

### Grafo ponderado y dirigido

La red social se modela como un grafo G = (V, E, w) donde:

- **V** es el conjunto de vértices (nodos), cada uno representando una persona.
- **E ⊆ V × V** es el conjunto de aristas dirigidas, donde (u, v) ∈ E indica que la persona u puede contagiar a la persona v.
- **w : E → [0, 1]** es la función de peso, que asigna a cada arista una probabilidad de contagio.

### Atributos del nodo

Cada nodo v ∈ V tiene un vector de atributos demográficos:

```
v = (id, nombre, edad, estrato, ocupacion, estado_SIRV)
```

Estos atributos no son decorativos — influyen directamente en el peso de las aristas adyacentes al nodo.

### Función de peso de arista

El peso de una arista (u → v) se calcula como:

```
w(u,v) = probBase × fEdad(u) × fEstrato(u) × fOcupacion(u)
```

Donde:
- `probBase` ∈ [0.1, 0.4] — probabilidad base de contagio de la enfermedad.
- `fEdad(u) = 1.0 + (edad_u / 100)` — personas mayores son más vulnerables.
- `fEstrato(u) = 1.0 + 0.1 × (6 - estrato_u)` — estrato bajo implica mayor exposición.
- `fOcupacion(u)` — factor según ocupación:

| Ocupación | Factor |
|---|---|
| Trabajador de salud | 1.40 |
| Trabajador informal | 1.30 |
| Estudiante | 1.20 |
| Empleado formal | 1.00 |
| Jubilado | 0.80 |

El resultado se normaliza para que `w(u,v) ∈ [0.05, 0.95]`.

---

## 2. Modelo de propagación — SIRV

El modelo SIRV (Susceptible – Infectado – Recuperado – Vacunado) es una extensión del clásico modelo SIR de epidemiología matemática, adaptado a simulación discreta sobre grafos.

### Estados posibles

Cada nodo en cada turno t se encuentra en exactamente uno de estos estados:

| Estado | Símbolo | Descripción |
|---|---|---|
| Susceptible | S | No ha sido infectado. Puede contraer la enfermedad. |
| Infectado | I | Tiene la enfermedad. Puede contagiar a sus vecinos S. |
| Recuperado | R | Superó la enfermedad. No puede reinfectarse ni contagiar. |
| Vacunado | V | Inmune desde el inicio. No puede infectarse. |

### Transiciones entre estados

Las transiciones ocurren una vez por turno (tiempo discreto):

```
S → I   con probabilidad w(infectado, susceptible)
I → R   cuando diasInfectado >= diasRecuperacion
S → V   antes de la simulación (estrategia de vacunación)
```

No existen las transiciones I→S, R→S ni V→S (el modelo asume inmunidad permanente).

### Ecuaciones de transición por turno t

Para cada nodo v con estado S en el turno t:

```
P(v pasa a I en t+1) = 1 - ∏ (1 - w(u,v))
                            u∈N(v), estado(u,t)=I
```

Donde N(v) es el conjunto de vecinos de v cuyos nodos apuntan hacia v con aristas de contagio.

En la implementación discreta se simplifica a: por cada vecino u infectado, se genera un número aleatorio r ∈ [0,1). Si r < w(u,v), el nodo v se infecta en el siguiente turno.

### Número reproductivo básico R₀

El R₀ estima cuántas personas nuevas infecta en promedio un nodo infectado durante su periodo de infección:

```
R₀ ≈ <k> × p × d
```

Donde:
- `<k>` = grado promedio de la red.
- `p` = probabilidad promedio de contagio por contacto.
- `d` = días promedio de infección (diasRecuperacion).

Si R₀ > 1 el brote se expande. Si R₀ < 1 se extingue. Las estrategias de vacunación buscan reducir R₀ por debajo de 1.

---

## 3. Tipos de redes sociales

En el proyecto de Matemáticas Discretas se estudiaron cuatro topologías de red. En el proyecto actual la red se genera con el **modelo de comunidades** (Stochastic Block Model), que es el más realista para comunidades humanas colombianas. Se describen los cuatro tipos como marco teórico:

### Red Aleatoria (Erdős–Rényi)
Cada par de nodos se conecta con probabilidad constante p, independientemente de cualquier estructura social. Genera redes homogéneas sin hubs ni comunidades claras.

Parámetro principal: p (probabilidad de conexión entre cualquier par).

### Red Small-World (Watts–Strogatz)
La mayoría de nodos están conectados localmente (como en una comunidad real), pero existe un pequeño porcentaje de conexiones aleatorias de largo alcance. Esto genera el fenómeno de "seis grados de separación": la distancia promedio entre cualquier par de nodos es muy corta a pesar de que cada nodo tiene pocos vecinos.

Parámetros: k (vecinos locales), β (probabilidad de reconexión aleatoria).

### Red Scale-Free (Barabási–Albert)
Algunos nodos concentran una cantidad desproporcionada de conexiones (hubs), mientras que la mayoría tiene muy pocas. La distribución de grados sigue una ley de potencias: P(k) ~ k^{-γ}. Modela redes como internet, redes de aeropuertos o redes de contacto sexual.

Mecanismo de formación: nuevos nodos se conectan con mayor probabilidad a los que ya tienen más conexiones (preferential attachment).

### Red de Comunidades (Stochastic Block Model) ← usada en este proyecto
Los nodos se agrupan en comunidades (familias, barrios). La probabilidad de conexión dentro de una comunidad es mucho mayor que entre comunidades. Modela fielmente las redes sociales humanas.

Parámetros: número de comunidades, probabilidad intra-comunidad p_in, probabilidad inter-comunidad p_out (p_in >> p_out).

---

## 4. Estrategias de vacunación

Todas las estrategias vacunan exactamente el **20% de los nodos susceptibles** antes de comenzar la simulación. El objetivo es identificar cuál subconjunto del 20% minimiza el impacto del brote. Se evalúan **cinco estrategias** en total.

### Estrategia 1 — Vacunación Aleatoria

**Fundamento:** No se usa información de la red. Se selecciona un subconjunto aleatorio de nodos susceptibles.

**Algoritmo:**
```
candidatos = lista de todos los nodos S
mezclar candidatos aleatoriamente
vacunar los primeros ⌊0.2 × |candidatos|⌋ nodos
```

**Complejidad:** O(N)

**Ventaja:** Simple, no requiere análisis de la red.
**Desventaja:** Alta varianza en resultados. No aprovecha la estructura de la red.

---

### Estrategia 2 — Vacunación por Hubs (Centralidad de Grado)

**Fundamento:** Los nodos con más conexiones (hubs) son los principales propagadores de la enfermedad. Vacunarlos interrumpe múltiples cadenas de contagio simultáneamente.

**Métrica:** Centralidad de grado — `deg(v) = |N(v)|` (número de vecinos).

**Algoritmo:**
```
para cada nodo v:
    calcular deg(v)
ordenar nodos por deg(v) descendente
vacunar el 20% superior
```

**Complejidad:** O(N + M) para calcular grados, O(N log N) para ordenar. Total: O(N log N)

**Ventaja:** Muy efectivo en redes Scale-Free donde los hubs concentran la mayoría de las conexiones.
**Desventaja:** En redes con comunidades bien separadas, puede no interrumpir los puentes entre comunidades.

---

### Estrategia 3 — Vacunación por Betweenness (Centralidad de Intermediación)

**Fundamento:** Algunos nodos actúan como puentes entre distintas comunidades. Aunque no tengan el mayor número de conexiones absolutas, son esenciales para que la infección salte de un grupo a otro.

**Métrica:** Centralidad de intermediación

```
CB(v) = Σ_{s≠v≠t} σ(s,t|v) / σ(s,t)
```

Donde:
- `σ(s,t)` = número total de caminos más cortos entre s y t.
- `σ(s,t|v)` = número de esos caminos que pasan por v.

**Algoritmo (aproximación con BFS):**
```
para cada nodo s:
    ejecutar BFS desde s
    contar cuántos caminos más cortos pasan por cada nodo v
acumular los conteos → score de intermediación por nodo
vacunar el 20% con mayor score
```

**Complejidad:** O(N × (N + M)) con BFS desde cada nodo.

**Ventaja:** Muy efectivo en redes con comunidades para bloquear la propagación inter-comunidad.
**Desventaja:** Costoso computacionalmente en redes grandes. En redes homogéneas su ventaja sobre Hubs es pequeña.

---

### Estrategia 4 — Vacunación dentro de Comunidades

**Fundamento:** En redes con estructura de comunidades, la propagación ocurre primero y con mayor velocidad *dentro* del grupo. Vacunar en las comunidades más densas interrumpe esos focos activos antes de que la infección pueda cruzar hacia otros grupos a través de los puentes inter-comunidad.

**Definición de comunidad usada:**
Se aproxima comunidad = estrato socioeconómico. En la red colombiana generada, los clusters familiares y vecinales se forman dentro del mismo estrato, por lo que el estrato es un proxy estructuralmente válido del grupo social.

**Métrica:** Densidad interna de aristas del grupo

```
densidad(g) = aristasInternas(g) / (|g| × (|g| - 1))
```

Donde `aristasInternas(g)` es el número de aristas cuyo origen Y destino pertenecen al mismo grupo g, y el denominador es el máximo posible en un grafo dirigido sin lazos.

**Algoritmo:**
```
para cada estrato g:
    calcular densidad(g)
ordenar estratos por densidad descendente
desde el estrato más denso:
    ordenar miembros por grado descendente
    vacunar hasta completar el 20%
```

**Complejidad:** O(N + M) — recorrido único de aristas para contar las internas.

**Ventaja:** muy efectiva cuando las comunidades son densas y bien separadas. Corta focos intra-grupo. Más eficiente computacionalmente que Betweenness e Híbrida.
**Desventaja:** no bloquea los puentes inter-comunidad; para eso Betweenness es mejor. En redes homogéneas su resultado se acerca a Aleatoria.

**Relación con Híbrida:**
Comunidades opera a nivel de **grupo** (decide qué comunidad priorizar).
Híbrida opera a nivel de **individuo** (score compuesto por cada nodo).
Son complementarias y su comparación directa es una de las hipótesis del proyecto.

---

### Estrategia 5 — Vacunación Híbrida por Score Compuesto ★ (aporte propio)

**Fundamento:** Las estrategias anteriores consideran solo la posición del nodo en la red. Sin embargo, en una comunidad real, la vulnerabilidad individual importa tanto como la conectividad. Una persona mayor de estrato 1 con muchas conexiones debería tener prioridad sobre un joven de estrato 4 igualmente conectado.

**Score compuesto:**

```
score(v) = α · CB_norm(v) + β · riesgo_edad(v) + γ · vulnerabilidad_estrato(v) + δ · deg_norm(v)
```

**Funciones auxiliares:**

```
CB_norm(v)                = CB(v) / CB_max          ∈ [0, 1]
deg_norm(v)               = deg(v) / deg_max        ∈ [0, 1]
riesgo_edad(v)            = edad(v) / 90            ∈ [0, 1]
vulnerabilidad_estrato(v) = (7 - estrato(v)) / 6    ∈ [0, 1]
```

**Ponderación sugerida:**

| Componente | Parámetro | Valor |
|---|---|---|
| Centralidad de intermediación | α | 0.35 |
| Riesgo por edad | β | 0.25 |
| Vulnerabilidad por estrato | γ | 0.25 |
| Centralidad de grado (normalizada) | δ | 0.15 |

**Algoritmo:**
```
calcular CB(v) y deg(v) para todos los nodos
para cada nodo v susceptible:
    calcular score(v) con la fórmula
insertar en PriorityQueue<Persona> ordenada por score descendente
vacunar el 20% con mayor score
```

**Complejidad:** O(N × (N + M)) por betweenness + O(N log N) por la cola de prioridad.

**Hipótesis:** esta estrategia debería superar a Betweenness puro en redes con comunidades de distintos estratos, porque prioriza nodos que son simultáneamente puentes entre comunidades Y perfiles de alto riesgo.

---

## 5. Mecanismo de actualización de pesos

### 5.1 Eventos por umbral (NPI)

Matemáticamente, los eventos representan una perturbación externa aplicada al factor acumulado en el turno t* en que se cruza el umbral θ:

```
factorEventos(t) = ∏ λ_k    para todo evento k con umbral θ_k ≤ porcentajeInfectados(t*)
```

Este factor se acumula multiplicativamente y se compone con los demás factores del turno (ver §5.2).

| Evento | Umbral θ | Factor λ | Efecto acumulado (los tres juntos) |
|---|---|---|---|
| Alerta leve | 0.30 | 0.80 | × 0.80 |
| Cuarentena | 0.50 | 0.50 | × 0.40 |
| Lockdown | 0.70 | 0.20 | × 0.08 |

### 5.2 Pesos adaptativos — reacción local y fatiga social (v11)

A partir de v11, el peso efectivo de cada arista se recalcula en cada turno como composición de tres factores:

```
w_ef(u→v, t) = clamp( w_base(u→v) × factorEventos(t) × vigilancia(v, t) × fatiga(t),
                       0.05, 0.95 )
```

**Factor de reacción local — vigilancia(v, t)**

El nodo destino v reduce la probabilidad de recibir contagio según la fracción de sus vecinos entrantes que están actualmente infectados:

```
vigilancia(v, t) = 1 − VIGILANCIA_MAX × (|{u : (u→v) ∈ E ∧ estado(u,t) = I}| / |{u : (u→v) ∈ E}|)

VIGILANCIA_MAX = 0.60
```

Interpretación: si todos los vecinos que apuntan a v están infectados, v es un 60% más cuidadoso y la prob de que cualquiera de ellos lo contagie baja en esa proporción. Si ninguno está infectado, no hay reducción. Este factor es completamente local: v reacciona a su entorno inmediato, no al estado global de la epidemia.

**Factor de fatiga social — fatiga(t)**

Si el número de infectados I(t) lleva UMBRAL = 5 turnos consecutivos sin crecer, la gente se relaja y los pesos aumentan gradualmente (hasta un máximo del 30%):

```
sea Δ = turnosDecreciendo(t) − UMBRAL_FATIGA_TURNOS

si Δ < 0:  fatiga(t) = 1.0               (no hay relajación)
si Δ ≥ 0:  fatiga(t) = 1.0 + min(0.30, Δ / 10 × 0.30)
```

Cuando I(t) vuelve a crecer, `turnosDecreciendo` se reinicia a 0 y `fatiga(t) = 1.0`. Este factor modela el efecto memoria: una epidemia que lleva semanas bajando lleva a la población a relajar sus precauciones, aunque los NPI aún tengan efecto (factorEventos < 1).

**Interacción entre factores**

Los tres factores se componen multiplicativamente. Ejemplo:

```
w_base = 0.50
factorEventos = 0.40  (ALERTA_LEVE × CUARENTENA = 0.80 × 0.50)
vigilancia(v) = 0.64  (40% de vecinos infectados → 1 - 0.6 × 0.4)
fatiga        = 1.20  (12 turnos sin crecer → 2/10 × 30% = +6%, pero supongamos 12 turnos)

w_ef = 0.50 × 0.40 × 0.64 × 1.20 = 0.154
```

La fatiga "empuja hacia arriba" contrarrestando parcialmente las reducciones de eventos y vigilancia, sin nunca superar el máximo del clamp (0.95).

---

## 6. BFS Ponderado — Camino de mayor contagio

**Objetivo:** dado el paciente cero, encontrar el camino de mayor probabilidad acumulada de contagio hacia cualquier nodo del grafo. Permite rastrear "por dónde viajó la infección".

**Definición:** el camino de mayor contagio entre u y v es la secuencia de nodos que maximiza el producto de las probabilidades de sus aristas:

```
max P(u → v) = max ∏ w(e_i)    para todo camino p de u a v
               p   e_i ∈ p
```

Trabajando en logaritmos (para evitar productos de flotantes muy pequeños):

```
max log P(u → v) = max Σ log(w(e_i))
```

**Implementación con PriorityQueue:**
```
cola = PriorityQueue ordenada por probabilidad acumulada descendente
insertar (nodoInicial, prob=1.0)
mientras cola no vacía:
    extraer nodo v con mayor prob acumulada
    si v == destino: retornar camino
    para cada vecino u de v:
        nuevaProb = probAcumulada(v) × w(v, u)
        si nuevaProb > mejorProb(u):
            insertar (u, nuevaProb) con referencia al camino
```

**Uso en el proyecto:** se ejecuta post-simulación para visualizar en GraphStream la cadena de contagio más probable desde el paciente cero, resaltando las aristas del camino con un color diferente.

---

## 7. Métricas de análisis comparativo

Al finalizar cada simulación se calculan las siguientes métricas para comparar estrategias:

### Pico máximo de infectados
```
I_max = max { I(t) : t = 0, 1, ..., T }
```
Indica la presión máxima sobre el sistema de salud. Menor es mejor.

### Turno del pico
```
t_pico = argmax_t { I(t) }
```
Un pico tardío significa que la estrategia retrasó la propagación.

### Duración del brote
```
T_brote = min { t : I(t) = 0 }
```
Turnos hasta que no quedan infectados activos.

### Total de afectados
```
Total = R(T_brote)
```
Número de personas que enfermaron (nodos en estado R al final). No incluye vacunados.

### Porcentaje de contención
```
Contencion(%) = (N - Total_afectados) / N × 100
```
Porcentaje de la población que nunca se infectó (suma de S finales + V).

### R₀ estimado post-simulación
```
R0_estimado = promedio de nuevos infectados por nodo infectado en turnos t=1,2,3
```
Permite comparar la velocidad inicial de propagación bajo cada estrategia.

---

## 8. Hipótesis a verificar

| # | Hipótesis |
|---|---|
| H1 | La vacunación por Hubs es más efectiva que la Aleatoria en redes con hubs claros. |
| H2 | La vacunación por Betweenness es más efectiva en redes con comunidades bien separadas. |
| H3 | La estrategia Híbrida supera a Betweenness puro en redes con disparidad de estratos socioeconómicos. |
| H4 | En redes densas (red grande ~300 nodos), los eventos por umbral tienen mayor impacto en la reducción del pico. |
| H5 | La vacunación Aleatoria puede ser comparable a Hubs en redes homogéneas donde no hay hubs dominantes. |
| H6 | Comunidades supera a Aleatoria y a Hubs en redes con clusters intra-estrato densamente conectados (red pequeña ~80 nodos). |
| H7 | Híbrida supera a Comunidades en la red grande (~300 nodos) porque incorpora el perfil demográfico individual, no solo la densidad del grupo. |

Estas hipótesis se contrastan ejecutando las **5 estrategias** sobre los 2 casos de prueba (red pequeña y red grande) y comparando las métricas del punto 7.

La comparación H6/H7 es de especial interés porque enfrenta directamente las dos estrategias nuevas: Comunidades (nivel grupo, O(N+M)) vs. Híbrida (nivel individuo, O(N×(N+M))). Si H7 se confirma, justifica el costo computacional adicional de Híbrida en redes grandes. Si H6 se confirma pero H7 no, Comunidades resulta ser la estrategia óptima por su mejor balance eficiencia-efectividad.
