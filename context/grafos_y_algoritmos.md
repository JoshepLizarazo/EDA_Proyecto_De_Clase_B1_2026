# Grafos y algoritmos en este proyecto

## 1. Qué es el grafo y cómo se construye

### Representación

La red social se modela como un **grafo dirigido y ponderado**. La clase central es `RedSocial`, que internamente usa una **lista de adyacencia**:

```
HashMap<Persona, List<Contacto>>
```

- Cada **nodo** es una `Persona` con atributos: `id`, `edad`, `estrato` (1–6) y `ocupacion`.
- Cada **arista** es un `Contacto` que contiene: `origen`, `destino` y `probContagio ∈ (0, 1]`.
- La dirección importa: `agregarContacto(A, B, p)` significa "A puede contagiar a B con probabilidad p". Para que B también pueda contagiar a A, se agrega la arista inversa explícitamente.

### Por qué lista de adyacencia y no matriz

Con N nodos y M aristas, la lista de adyacencia ocupa O(N + M). Una matriz ocuparía O(N²), que para N = 500 sería 250 000 entradas, la mayoría vacías. La red generada es **dispersa** (cada persona tiene ~5–15 contactos), por lo que la lista es la representación correcta.

---

## 2. Cómo se construye la red — GeneradorPoblacion

La construcción ocurre en **6 fases** encadenadas para imitar la estructura social colombiana:

### Fase 1 — Generar N personas

Se crean `N` nodos con distribuciones demográficas basadas en datos del DANE:

| Atributo  | Distribución |
|-----------|-------------|
| Edad      | 0–14: 24%, 15–35: 38%, 36–59: 26%, 60–90: 12% |
| Estrato   | 1: 30%, 2: 30%, 3: 30%, 4: 5%, 5: 3%, 6: 2% |
| Ocupación | Correlacionada con edad (jóvenes → estudiante; mayores → jubilado) |

### Fase 2 — Clusters familiares (aristas de alta probabilidad)

Los nodos se dividen en grupos de 4–7 personas que simulan familias. Dentro de cada familia, **todos los pares están conectados bidireccionalmente** con `probBase` entre 0.30 y 0.40, que es alta porque refleja la convivencia diaria. Esto crea subgrafos casi completos (cliques aproximadas) dentro de cada familia.

### Fase 3 — Vecindarios (aristas de probabilidad media)

Las familias del mismo estrato socioeconómico se conectan entre sí con `probBase` entre 0.10 y 0.18. Se conectan familias a distancias 1, 2 y 3 (con probabilidad de conexión decreciente: 100%, 55%, 30%), y se relaja la restricción de estrato a mayor distancia. Esto crea la **estructura de comunidades** que explotan las estrategias de vacunación basadas en grupos.

### Fase 4 — Hubs comunitarios (aristas de probabilidad moderada)

Se crean 2–3 nodos que simulan lugares de alta concentración (mercado, iglesia, transporte público). Cada hub conecta personas de distintos clusters con `probBase` entre 0.08 y 0.15. Esto introduce **hubs de grado alto** que las estrategias Hubs y BFSPonderado priorizan.

### Fase 4.5 — Conexiones de largo alcance (efecto small-world)

Se agregan ~N/15 aristas aleatorias entre nodos de comunidades lejanas, con `probBase` baja (0.05–0.13). Esto imita conocidos de trabajo, redes sociales digitales o viajes, y produce el efecto **small-world**: el diámetro de la red se reduce drásticamente aunque la mayoría de conexiones sean locales.

### Fase 5 — Garantizar conectividad

Se hace un BFS no dirigido para detectar componentes conexas. Si hay más de una, se unen con una arista bidireccional de baja probabilidad entre un nodo del componente menor y uno del componente mayor. Esto garantiza que la epidemia puede alcanzar en teoría a cualquier nodo desde cualquier otro.

### Fórmula del peso de arista

```
probContagio = clamp(probBase × fEdad(u) × fEstrato(u) × fOcupacion(u), 0.05, 0.95)
```

Los factores del nodo **origen** (el que contagia) aumentan o reducen la probabilidad base según su perfil. Por ejemplo, una persona mayor tiene mayor factor de edad (es más propensa a contagiar si está enferma), y los trabajadores de salud tienen factor de ocupación alto por mayor exposición.

---

## 3. Modelo epidemiológico — ModeloSIRV

El modelo es **SIRV discreto**: cada turno representa un día de simulación y todos los cambios de estado se aplican al final (ejecución sincrónica).

### Estados

```
S (Susceptible) ──infecta──► I (Infectado) ──recupera──► R (Recuperado)
V (Vacunado) ────────────────────────────────────────────────────────────── (inmune, estático)
```

### Dinámica por turno

1. **Contagio**: para cada arista (INFECTADO → SUSCEPTIBLE), se lanza un número aleatorio `r ∈ [0, 1)`. Si `r < probContagio(arista)`, el susceptible pasa a infectado.
2. **Recuperación**: cada infectado acumula `diasInfectado`. Cuando `diasInfectado >= diasRecuperacion`, pasa a recuperado.
3. **Sincronía**: los cambios se aplican todos al final. Sin esto, un nodo infectado en el turno `t` podría contagiar en el mismo turno `t`.
4. **Eventos NPI** (`GestorEventos`): si el porcentaje de infectados supera un umbral (30%, 50%, 70%), se multiplican los pesos de TODAS las aristas por un factor reductor (0.80, 0.50, 0.20 respectivamente). Los factores son acumulativos y se aplican una sola vez cada uno.

---

## 4. Estrategias de vacunación

Todas las estrategias vacunan exactamente el **20% de los nodos susceptibles** antes de que inicie el brote (vacunación preventiva). El orden en que se seleccionan esos nodos es lo que diferencia a cada estrategia.

---

### 4.1 Vacunación Aleatoria — `VacunacionAleatoria`

**Tipo**: línea base de comparación  
**Complejidad**: O(N)

**Cómo funciona**: baraja la lista de susceptibles con una semilla fija y vacuna los primeros 20%. No usa ninguna información de la red.

**Por qué importa**: es la cota inferior de calidad. Si otra estrategia no la supera, significa que la estructura de la red no aporta información útil para decidir a quién vacunar.

---

### 4.2 Vacunación por Hubs — `VacunacionHubs`

**Tipo**: centralidad de grado  
**Complejidad**: O(N log N)

**Cómo funciona**: ordena los susceptibles por **grado saliente** (número de aristas que salen del nodo) de mayor a menor, y vacuna el 20% con mayor grado.

**Intuición**: un hub con 20 conexiones puede contagiar hasta 20 personas en un turno. Al vacunarlo, se eliminan esas 20 aristas de contagio de golpe. En redes con distribución de grado heterogénea (como las redes sociales reales), vacunar el 20% de hubs puede ser equivalente a vacunar el 60–70% de forma aleatoria.

**Limitación**: no detecta nodos con grado moderado pero que actúan de puente entre comunidades. Esos son más importantes de lo que su grado sugiere.

---

### 4.3 Vacunación por Betweenness — `VacunacionBetweenness`

**Tipo**: centralidad de intermediación (algoritmo de Brandes)  
**Complejidad**: O(N × (N + M))

**Cómo funciona**: calcula la **betweenness centrality** de cada nodo y vacuna el 20% con mayor valor.

**Definición de betweenness**:
```
CB(v) = Σ_{s≠v≠t} [ σ(s,t|v) / σ(s,t) ]
```
donde `σ(s,t)` es el número de caminos más cortos entre `s` y `t`, y `σ(s,t|v)` es cuántos de esos caminos pasan por `v`. Un CB alto significa que `v` es un "puente": muchos pares de nodos dependen de él para comunicarse.

**Algoritmo de Brandes** (dos fases por cada nodo fuente `s`):
1. **BFS hacia adelante**: calcula `σ[v]` (caminos mínimos desde `s` hasta `v`), `d[v]` (distancia) y `pred[v]` (predecesores). Los nodos se apilan en orden BFS.
2. **Retropropagación**: recorre la pila al revés acumulando la dependencia `δ[v]`:
   ```
   δ[v] += (σ[v] / σ[w]) × (1 + δ[w])   para cada predecesor v de w
   ```
   Al final: `CB[w] += δ[w]`.

**Intuición**: si vacunamos el puente entre dos comunidades, el virus queda atrapado en su comunidad de origen aunque siga propagándose internamente.

**Limitación**: la más costosa computacionalmente. No considera el perfil de riesgo individual.

---

### 4.4 Vacunación por Comunidades — `VacunacionComunidades`

**Tipo**: estructura de comunidades (densidad interna de aristas)  
**Complejidad**: O(N + M)

**Cómo funciona**: usa el **estrato socioeconómico** como proxy de comunidad (en la red colombiana generada, los clusters familiares y vecinales se forman dentro del mismo estrato). Calcula la **densidad interna de aristas** de cada estrato y prioriza vacunar en los estratos más densos.

**Fórmula de densidad**:
```
densidad(g) = aristasInternas(g) / (|g| × (|g| - 1))
```
donde `aristasInternas(g)` son las aristas cuyo origen Y destino pertenecen al grupo `g`, y el denominador es el máximo posible de aristas dirigidas en un grupo de `|g|` nodos.

**Pasos**:
1. Agrupar susceptibles por estrato.
2. Calcular densidad interna de cada grupo.
3. Ordenar grupos de mayor a menor densidad.
4. Dentro de cada grupo, priorizar por grado (más conectado primero).
5. Vacunar del grupo más denso al menos denso hasta completar el 20%.

**Intuición**: en una red con comunidades, la propagación ocurre primero y más rápido dentro del grupo. Cortar el contagio intra-grupo antes de que el virus salte a otras comunidades es más efectivo que cortar los puentes (Betweenness) cuando la epidemia aún no ha salido del foco inicial.

**Limitación**: no bloquea los puentes inter-comunidad. En redes muy homogéneas, se acerca al resultado aleatorio.

---

### 4.5 Vacunación BFS Ponderado — `VacunacionBFSPonderado`

**Tipo**: rutas de mayor probabilidad de contagio (aproximación de Betweenness)  
**Complejidad**: O(K × N × (N + M) log N), con K = 5

**Cómo funciona**:
1. Selecciona los K nodos de mayor grado como **candidatos a paciente cero** (los más expuestos a recibir el contagio inicial).
2. Desde cada candidato, ejecuta un **BFS Ponderado** (Dijkstra maximizando probabilidad) hacia cada uno de los demás susceptibles, obteniendo el camino de mayor probabilidad acumulada.
3. Cada nodo **intermedio** en esos caminos acumula un score igual a la probabilidad acumulada del camino. Los nodos que aparecen en muchas rutas críticas reciben score alto.
4. Vacuna el 20% con mayor score.

**BFS Ponderado — analogía con Dijkstra**:
- En Dijkstra se minimiza la suma de pesos (`dist[v] = ∞` al inicio, se relaja por suma).
- Aquí se maximiza el producto de probabilidades (`prob[v] = 0` al inicio, se relaja por producto):
  ```
  nuevaProb = prob[u] × probContagio(u → v)
  Si nuevaProb > prob[v] → actualizar y re-encolar v
  ```
- La cola de prioridad (max-heap) garantiza que la primera vez que se extrae un nodo, su probabilidad es ya la máxima posible (mismo razonamiento que Dijkstra con pesos ≥ 0).

**Relación con Betweenness**: ambos identifican nodos importantes en rutas de propagación, pero BFSPonderado usa la **probabilidad de la ruta** en lugar de contar caminos mínimos, y solo evalúa K focos en lugar de todos los nodos. Es una aproximación más rápida y epidemiológicamente más directa.

---

### 4.6 Vacunación Híbrida — `VacunacionHibrida`

**Tipo**: score compuesto (estructura de red + perfil individual)  
**Complejidad**: O(N × (N + M)) — dominado por Betweenness

**Cómo funciona**: calcula un **score compuesto** para cada nodo combinando cuatro factores normalizados:

```
score(v) = 0.35 × CB_norm(v)
         + 0.25 × (edad / 90)
         + 0.25 × ((7 - estrato) / 6)
         + 0.15 × grado_norm(v)
```

| Factor | Peso | Significado |
|--------|------|-------------|
| Betweenness normalizado | 0.35 | Posición estructural en la red (¿es puente?) |
| Riesgo por edad | 0.25 | Mayor edad → mayor mortalidad potencial |
| Vulnerabilidad por estrato | 0.25 | Estrato bajo → menos acceso a atención médica |
| Grado normalizado | 0.15 | Cuántas personas puede contagiar directamente |

**Justificación de pesos**: la suma de factores de red (0.35 + 0.15 = 0.50) es igual a la suma de factores individuales (0.25 + 0.25 = 0.50). Esto produce una estrategia equilibrada entre cortar cadenas de contagio y proteger a los más vulnerables.

**Diferencia con Comunidades**: Comunidades decide a **nivel de grupo** (qué estrato vacunar primero); Híbrida decide a **nivel de individuo** (score personalizado). Híbrida es más precisa; Comunidades es más interpretable.

---

## 5. Comparación de estrategias

| Estrategia | Base teórica | Complejidad | Fortaleza | Debilidad |
|------------|-------------|-------------|-----------|-----------|
| Aleatoria | Ninguna | O(N) | Rapidísima, línea base | Ignora la red |
| Hubs | Grado (local) | O(N log N) | Efectiva con hubs pronunciados | No detecta puentes |
| Betweenness | Intermediación (global) | O(N²) | Detecta puentes inter-comunidad | Costosa, sin perfil individual |
| Comunidades | Densidad intra-grupo | O(N + M) | Corta propagación interna | No bloquea puentes inter-grupo |
| BFSPonderado | Probabilidad de ruta | O(K × N log N) | Más barata que Betweenness, usa probabilidades reales | Solo evalúa K focos |
| Híbrida | Score compuesto | O(N²) | Combina red + perfil individual | Costosa; pesos son heurísticos |

---

## 6. Flujo completo de la simulación

```
GeneradorPoblacion.generar(N, semilla)
        │
        ▼
  RedSocial (grafo dirigido ponderado)
        │
        ├──► [Opcional] CargadorRedCSV (carga desde archivo)
        │
        ▼
VacunacionService.aplicar(estrategia)    ← selecciona una de las 6 estrategias
        │
        ▼
IniciarSimulacionCommand.ejecutar()
        │
        ├── setearPacienteCero(cantidad, random)   ← infecta N nodos iniciales
        │
        └── loop hasta I = 0 o turno máximo:
              │
              ├── ModeloSIRV.simularTurno(red)      ← propaga la epidemia
              ├── GestorEventos.evaluar(red, turno)  ← aplica medidas NPI si corresponde
              └── registrar {S, I, R, V} del turno
        │
        ▼
ResultadoSimulacionDto  →  ExportadorResultados / GeneradorReportePDF
```

---

## 7. Propiedades de la red generada

La red producida por `GeneradorPoblacion` tiene propiedades que la hacen realista para modelar epidemias:

- **Estructura de comunidades**: los estratos forman grupos densos internamente y poco conectados entre sí, igual que en redes sociales reales.
- **Hubs**: los nodos comunitarios (mercado, iglesia) tienen grado mucho mayor que el promedio.
- **Efecto small-world**: el diámetro de la red es pequeño gracias a las conexiones de largo alcance, aunque la mayoría de contactos son locales.
- **Pesos heterogéneos**: cada arista tiene su propia probabilidad de contagio calibrada por el perfil demográfico del nodo origen.

Estas propiedades explican por qué las estrategias avanzadas (Betweenness, Comunidades, Híbrida) superan a la aleatoria: la estructura de la red sí contiene información valiosa para decidir a quién vacunar primero.
