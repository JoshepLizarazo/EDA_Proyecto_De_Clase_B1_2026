# Grafos y algoritmos — documentación técnica completa

Este documento explica en detalle la estructura de grafos que usa el proyecto, cómo se construye, qué representa cada elemento, y cómo funciona cada algoritmo implementado sobre él. Está pensado para que cualquier persona que lea el código pueda entender el porqué detrás de cada decisión.

---

## 1. Qué es el grafo y qué representa

El proyecto simula la propagación de una epidemia en una **red social colombiana**. El instrumento central para modelar esa red es un **grafo dirigido y ponderado**.

### Qué significa cada elemento del grafo

| Elemento del grafo | Qué representa en el dominio |
|--------------------|------------------------------|
| Nodo (`Persona`) | Una persona con atributos: `id`, `edad`, `estrato` (1–6), `ocupacion`, `estado` SIRV |
| Arista (`Contacto`) | Que la persona origen **puede contagiar** a la persona destino |
| Peso de la arista | La **probabilidad** de que ese contagio ocurra en un turno (rango `[0.05, 0.95]`) |
| Dirección | El contagio no es simétrico: A puede contagiar a B con prob 0.40, pero B a A con prob 0.15 |

### Estructura interna — lista de adyacencia

```
RedSocial
  └── HashMap<Persona, List<Contacto>>

  P001 → [ Contacto(P001→P002, 0.42),  Contacto(P001→P003, 0.31) ]
  P002 → [ Contacto(P002→P001, 0.25),  Contacto(P002→P004, 0.18) ]
  P003 → [ Contacto(P003→P001, 0.28) ]
  P004 → [ ]
```

Se eligió lista de adyacencia (y no matriz de adyacencia) porque la red es **dispersa**: con N = 500 personas cada una tiene en promedio 5–15 contactos, por lo que la lista ocupa O(N + M) mientras que una matriz ocuparía O(N²) = 250 000 celdas, la mayoría vacías.

### Por qué el grafo es dirigido

La convención `agregarContacto(A, B, p)` significa "A puede contagiar a B con probabilidad p". El peso de la arista A→B se calcula usando los **factores de riesgo del nodo A** (el que contagia). Si se quiere que B también pueda contagiar a A, se agrega explícitamente la arista inversa B→A con los factores de B. Esto permite que, por ejemplo, un trabajador de salud (factor 1.40) tenga aristas salientes más pesadas que un jubilado (factor 0.80), aunque ambos tengan el mismo número de contactos.

---

## 2. Cómo se construye la red — `GeneradorPoblacion`

La red no se construye al azar puro. Pasa por **6 fases encadenadas** que imitan la estructura social real colombiana. El resultado es una red con propiedades de redes sociales reales: comunidades densas, hubs, efecto small-world.

```
Fase 1: Generar N personas con atributos demográficos DANE
    ↓
Fase 2: Clusters familiares (aristas densas de alta probabilidad)
    ↓
Fase 3: Vecindarios (aristas moderadas entre familias del mismo estrato)
    ↓
Fase 4: Hubs comunitarios (mercado, iglesia, transporte — aristas cruzadas)
    ↓
Fase 4.5: Conexiones de largo alcance (efecto small-world)
    ↓
Fase 5: Garantizar conectividad (sin componentes aislados)
```

### Fase 1 — Distribuciones demográficas

Las personas se generan con distribuciones basadas en datos del DANE:

```
Edad:
  0–14  años → 24%  (niños, baja movilidad)
  15–35 años → 38%  (adultos jóvenes, máxima interacción social)
  36–59 años → 26%  (adultos, interacción laboral)
  60–90 años → 12%  (mayores, mayor vulnerabilidad)

Estrato:
  1 → 30%  (el más común; mayor factor de exposición)
  2 → 30%
  3 → 30%
  4 →  5%
  5 →  3%
  6 →  2%  (el menos común; menor factor de exposición)

Ocupación (correlacionada con edad):
  <18 años  → siempre "estudiante"
  ≥65 años  → siempre "jubilado"
  resto     → estudiante 35%, informal 25%, empleado 25%, salud 10%, jubilado 5%
```

### Fórmula del peso de cada arista

Antes de agregar cualquier arista, se calcula su `probContagio` así:

```
probContagio = clamp( probBase × fEdad(origen) × fEstrato(origen) × fOcupacion(origen),
                      0.05, 0.95 )

fEdad(u)      = 1.0 + (edad / 100)          → edad=50 da factor 1.50
fEstrato(u)   = 1.0 + 0.1 × (6 - estrato)  → estrato=1 da factor 1.50; estrato=6 da 1.00
fOcupacion(u) = salud:1.40 | informal:1.30 | estudiante:1.20 | empleado:1.00 | jubilado:0.80
```

**Ejemplo concreto**: una enfermera de 50 años de estrato 2 tiene:
```
fEdad = 1.50, fEstrato = 1.40, fOcupacion = 1.40
probContagio = clamp(0.20 × 1.50 × 1.40 × 1.40, 0.05, 0.95)
             = clamp(0.588, 0.05, 0.95)
             = 0.59
```
Esa arista tiene casi un 60% de probabilidad de contagio por turno, frente al 0.20 base de un empleado joven de estrato 6 sin ningún factor amplificador.

### Fases 2 y 3 — Por qué se forman comunidades

Las familias (fase 2) son **subgrafos casi completos** (cliques): todos los miembros están conectados entre sí bidireccionalmente con `probBase` 0.30–0.40. Visualmente:

```
Familia A (estrato 2):           Familia B (estrato 2):
  P001 ←──→ P002                   P006 ←──→ P007
     ↑ ↘   ↙ ↑                        ↑ ↘   ↙ ↑
      P003 ←→ P004                    P008 ←→ P009

Vecindario (aristas entre A y B, probBase 0.10–0.18):
  P001 ←──→ P007   (1 o 2 aristas bidireccionales)
```

Estas cliques son exactamente las "comunidades" que detecta `VacunacionComunidades` midiendo densidad interna.

### Fase 4 — Hubs (nodos de grado muy alto)

Se crean 2–3 nodos que simulan lugares de alta concentración (mercado, iglesia). Cada hub conecta `N/10` personas de **distintos clusters**:

```
                  Hub (mercado)
                 /    |    |    \
              P001  P043  P078  P099   ← personas de distintas familias
```

Estos hubs tienen grado mucho mayor que el promedio. Son exactamente los nodos que `VacunacionHubs` y `VacunacionBFSPonderado` priorizan.

---

## 3. Modelo epidemiológico — `ModeloSIRV`

### Los 4 estados del grafo

Cada nodo (`Persona`) tiene un estado que cambia durante la simulación:

```
         contagio               recuperación
SUSCEPTIBLE ───────► INFECTADO ────────────► RECUPERADO
     │                                            │
     │ vacunación                                 │
     └──────────► VACUNADO                        │
                     │                            │
                     └──── inmune (no transiciona)─┘
```

- **S → I**: ocurre si hay una arista (INFECTADO → SUSCEPTIBLE) y el número aleatorio `r < probContagio(arista)`.
- **I → R**: ocurre cuando `diasInfectado >= diasRecuperacion` (parámetro configurable).
- **S → V**: se aplica antes de que inicie el brote (todas las estrategias de vacunación hacen esta transición).
- **Inmunidad permanente**: R y V nunca vuelven a S. No hay reinfecciones en el modelo.

### Por qué la ejecución es sincrónica (por lotes)

En cada turno, los cambios **no se aplican nodo a nodo** sino todos al final. Esto es crítico:

```
Sin sincronía (INCORRECTO):
  Turno t — se itera en orden P001, P002, P003...
  P001 (INFECTADO) contagia a P002 → P002 pasa a INFECTADO en el turno t
  P002 (ahora INFECTADO en el mismo turno t) contagia a P003 → P003 se infecta en el turno t
  → El virus se propaga de forma irreal, dependiendo del orden de iteración

Con sincronía (CORRECTO):
  Turno t — se recolectan TODOS los que se van a infectar primero
  Luego se aplican TODOS los cambios al mismo tiempo
  → P003 puede infectarse en el turno t+1, nunca en el mismo t
```

La implementación usa un `Set<Persona> nuevosInfectados` que acumula todos los candidatos antes de aplicar cualquier cambio de estado.

### GestorEventos + AjustadorPesosAdaptativo — pesos dinámicos adaptativos (v11)

A partir de v11, el peso efectivo de cada arista se recalcula en cada turno como composición de tres factores independientes:

```
probEfectiva(u→v) = clamp( probBase(u→v) × factorEventos × vigilancia(v) × factorFatiga )
```

**GestorEventos** ya no modifica aristas directamente. Solo rastrea el factor NPI acumulado:

```
Umbral 30% infectados → factorEventos × 0.80  (ALERTA_LEVE)
Umbral 50% infectados → factorEventos × 0.50  (CUARENTENA)
Umbral 70% infectados → factorEventos × 0.20  (LOCKDOWN)

Si se disparan los tres: factorEventos = 0.80 × 0.50 × 0.20 = 0.08
```

**AjustadorPesosAdaptativo** aplica los tres factores cada turno:

```
vigilancia(v) = 1 − 0.60 × (vecinosEntrantes_infectados(v) / vecinosEntrantes_totales(v))
  → si el 100% de los vecinos que apuntan a v están infectados: vigilancia = 0.40 (−60%)
  → si ninguno está infectado: vigilancia = 1.00 (sin efecto)

fatiga(t):
  si infectados lleva ≥ 5 turnos sin crecer → sube hasta +30% gradualmente
  si infectados vuelve a crecer → se reinicia a 1.0
```

`Contacto` almacena `probContagioBase` (inmutable) y `probContagio` (efectiva del turno). El ajustador resetea `probContagio` desde la base cada turno, eliminando el problema de acumulación entre turnos.

---

## 4. Estrategias de vacunación — los algoritmos sobre el grafo

Todas las estrategias vacunan exactamente el **20% de los nodos susceptibles** antes de que inicie el brote. La diferencia entre ellas es **qué nodos eligen** para vacunar, y eso depende de qué información del grafo usan.

> **Comparación justa (v8).** El paciente cero (5% de la población) se fija
> **antes** de vacunar y es idéntico para las seis estrategias (misma red + misma
> semilla). Por eso la vacunación opera sobre los susceptibles restantes y nunca
> recae sobre un nodo ya infectado: las seis estrategias arrancan exactamente del
> mismo estado inicial.

---

### 4.1 Vacunación Aleatoria — `VacunacionAleatoria`

**Base teórica**: ninguna (línea base)  
**Complejidad**: O(N)  
**Información del grafo que usa**: ninguna

```
Algoritmo:
  1. Obtener lista de nodos SUSCEPTIBLE
  2. Barajar con semilla fija (reproducibilidad)
  3. Tomar los primeros 20%
  4. Cambiar su estado a VACUNADO
```

**Para qué sirve**: es la **cota inferior**. Cualquier estrategia que use información del grafo debe superarla para tener valor. Si Betweenness o Hubs no la superan, hay un bug o los parámetros están mal.

---

### 4.2 Vacunación por Hubs — `VacunacionHubs`

**Base teórica**: centralidad de grado  
**Complejidad**: O(N log N)  
**Información del grafo que usa**: grado saliente de cada nodo

```
Algoritmo:
  1. Obtener lista de nodos SUSCEPTIBLE
  2. Ordenar por grado saliente DESCENDENTE  ← O(N log N)
  3. Vacunar el 20% con mayor grado
```

**Intuición con un ejemplo**:

```
Red (flechas = puede contagiar):
  Hub:  P001 → P010, P011, P012, P013, P014, P015, P016  (grado 7)
  Nodo: P002 → P003                                       (grado 1)

Sin vacunar P001: en un turno puede infectar hasta 7 personas
Vacunando P001:   se bloquean las 7 aristas salientes de golpe

Vacunar 1 hub = efecto equivalente a bloquear 7 aristas
Vacunar 1 nodo normal = bloquear 1 arista
```

**Limitación**: no detecta nodos "puente" con grado moderado. Un nodo con grado 3 que conecta dos comunidades de 50 personas puede ser más estratégico que un hub con grado 10 dentro de una sola comunidad.

---

### 4.3 Vacunación por Betweenness — `VacunacionBetweenness`

**Base teórica**: centralidad de intermediación — algoritmo de Brandes (2001)  
**Complejidad**: O(N × (N + M)) ≈ O(N²) para redes dispersas  
**Información del grafo que usa**: posición global de cada nodo en el grafo

#### Qué mide Betweenness

```
CB(v) = Σ_{s≠v≠t} [ σ(s,t|v) / σ(s,t) ]

σ(s,t)   = número de caminos más cortos entre s y t
σ(s,t|v) = cuántos de esos caminos pasan por v
```

Un nodo con CB alto aparece en el camino más corto entre muchos pares de nodos. En el contexto epidemiológico: **si se infecta, actúa de puente y lleva el virus de una comunidad a otra**.

**Ejemplo visual**:

```
Comunidad A (nodos 1-5)        Comunidad B (nodos 6-10)
   1─2─3                           7─8─9
   │   │                           │   │
   4───5 ──── PUENTE ────────────── 6──10
             (CB alto)
```

El nodo "PUENTE" tiene CB alto aunque su grado sea solo 2 (una arista hacia A, una hacia B). Si se vacuna, el virus queda atrapado en A.

#### Algoritmo de Brandes — paso a paso

Para cada nodo fuente `s` se ejecutan dos fases:

**Fase 1 — BFS hacia adelante** (calcula caminos mínimos):

```
Inicializar:
  sigma[s] = 1.0  (hay exactamente 1 camino de s a s mismo)
  sigma[v] = 0.0  para todo v ≠ s
  d[s] = 0, d[v] = -1  (no visitados)
  pred[v] = []   (predecesores en caminos mínimos)

BFS normal + conteo de caminos:
  Al visitar w desde v:
    Si d[w] == -1:          → primera visita, d[w] = d[v] + 1
    Si d[w] == d[v] + 1:   → w es alcanzable en camino mínimo por v
                               sigma[w] += sigma[v]
                               pred[w].add(v)

Los nodos se apilan en orden de visita BFS (pila invertida = orden decreciente de distancia).
```

**Fase 2 — Retropropagación** (acumula la contribución al betweenness):

```
Para cada w en la pila (del más lejano al más cercano):
  Para cada v en pred[w]:
    delta[v] += (sigma[v] / sigma[w]) × (1 + delta[w])

  Si w ≠ s:
    CB[w] += delta[w]
```

La expresión `(sigma[v] / sigma[w])` es la fracción de caminos mínimos desde `s` hasta `w` que pasan por `v`. Multiplicado por `(1 + delta[w])` acumula también la dependencia de todos los nodos alcanzados desde `w`.

**¿Por qué funciona la retropropagación?**

Imagine que desde `s` hay 3 caminos mínimos hasta `t`, y 2 de ellos pasan por `v`. La contribución de `v` para el par (s,t) es 2/3. Brandes evita recalcular esto para cada par (s,t) propagando esta fracción hacia atrás desde los nodos más lejanos.

---

### 4.4 Vacunación por Comunidades — `VacunacionComunidades`

**Base teórica**: densidad interna de aristas por grupo  
**Complejidad**: O(N + M)  
**Información del grafo que usa**: aristas internas de cada comunidad (estrato)

#### Por qué el estrato como proxy de comunidad

En la red generada por `GeneradorPoblacion`, los clusters familiares y vecinales se forman dentro del mismo estrato (las familias se conectan con otras familias de `|estratoA - estratoB| ≤ 1`). Por eso el estrato es un proxy válido de comunidad social.

#### Qué mide la densidad interna

```
densidad(g) = aristasInternas(g) / (|g| × (|g| - 1))

aristasInternas(g) = aristas cuyo ORIGEN y DESTINO ambos están en el grupo g
denominador         = máximo de aristas dirigidas posibles sin lazos en |g| nodos
```

Un grupo con densidad 0.8 tiene el 80% de todas las aristas posibles entre sus miembros. Eso es un foco de contagio activo: el virus se propaga muy rápido internamente.

**Algoritmo completo**:

```
1. Agrupar susceptibles por estrato:
   estrato_1 = [P001, P003, P007, ...]
   estrato_2 = [P002, P008, P011, ...]
   ...

2. Para cada grupo, contar aristas internas:
   Para P001 (estrato 1): revisar sus contactos
     P001 → P003 (estrato 1) → INTERNA ✓
     P001 → P002 (estrato 2) → EXTERNA ✗

   densidad(estrato_1) = aristasInternas / (N_1 × (N_1 - 1))

3. Ordenar grupos: [estrato_1: 0.72, estrato_3: 0.61, estrato_2: 0.45, ...]

4. Vacunar desde el grupo más denso:
   - Dentro de cada grupo, ordenar por grado descendente
   - Vacunar hasta completar el 20% del total susceptible
```

**Intuición**: si el estrato 1 tiene densidad 0.72, hay muchas aristas internas → el virus se propaga muy rápido ahí. Hay que cortar esos contagios antes de que el virus salte a otros grupos.

---

### 4.5 BFS Ponderado — `BFSPonderado` y `VacunacionBFSPonderado`

**Base teórica**: Dijkstra adaptado a maximizar productos de probabilidades  
**Complejidad**: O((N + M) log N) por ejecución  
**Información del grafo que usa**: pesos de las aristas (probabilidades de contagio)

#### La analogía con Dijkstra

Dijkstra encuentra el **camino de mínima suma de pesos** (distancias). BFSPonderado encuentra el **camino de máximo producto de probabilidades** (contagio más probable):

```
Dijkstra (minimizar suma):
  dist[origen] = 0, dist[resto] = ∞
  Relajación: si dist[u] + peso(u→v) < dist[v] → actualizar dist[v]

BFSPonderado (maximizar producto):
  prob[origen] = 1.0, prob[resto] = 0.0
  Relajación: si prob[u] × probContagio(u→v) > prob[v] → actualizar prob[v]
```

La cola de prioridad es un **max-heap** en lugar de min-heap: se procesa primero el nodo con mayor probabilidad acumulada conocida. La propiedad de optimalidad es la misma: la primera vez que se extrae un nodo, su probabilidad es ya la máxima posible (porque las probabilidades de arista están en (0,1], el producto solo puede decrecer).

**Ejemplo paso a paso**:

```
Grafo:  A ─0.8─► B ─0.9─► D
        A ─0.5─► C ─0.7─► D

Desde A hacia D:
  Ruta A→B→D: 0.8 × 0.9 = 0.72
  Ruta A→C→D: 0.5 × 0.7 = 0.35

BFSPonderado devuelve: [A, B, D] con prob = 0.72
```

#### Cómo lo usa `VacunacionBFSPonderado` para decidir quién vacunar

```
1. Elegir K=5 candidatos a paciente cero (los 5 de mayor grado — más expuestos)

2. Desde cada candidato, ejecutar BFSPonderado hacia TODOS los demás susceptibles:
   foco P001 → destino P002: camino [P001, P045, P002], prob = 0.63
                                          ↑ intermedio → score[P045] += 0.63
   foco P001 → destino P003: camino [P001, P045, P088, P003], prob = 0.41
                                      intermedios → score[P045] += 0.41, score[P088] += 0.41

3. Los nodos que aparecen como intermediarios en muchas rutas críticas acumulan score alto.
   Son los "cuellos de botella" de la propagación.

4. Vacunar el 20% con mayor score acumulado.
```

**Diferencia con Betweenness**: ambos identifican nodos estratégicos en rutas de propagación, pero:
- Betweenness cuenta **caminos mínimos** (sin importar el peso)
- BFSPonderado usa la **probabilidad real de la ruta** (captura la epidemiología directamente)
- Betweenness evalúa todos los pares O(N²); BFSPonderado solo evalúa K focos O(K×N log N)

---

### 4.6 Vacunación Híbrida — `VacunacionHibrida`

**Base teórica**: score compuesto — estructura de red + perfil individual  
**Complejidad**: O(N × (N + M)) — dominado por el cálculo de Betweenness  
**Información del grafo que usa**: betweenness, grado, y atributos de cada nodo

#### La fórmula del score

```
score(v) = α × CB_norm(v) + β × edad_norm(v) + γ × estrato_vulnerabilidad(v) + δ × grado_norm(v)

Donde:
  α = 0.35 — betweenness normalizado:  CB(v) / max(CB)        → ¿es puente en la red?
  β = 0.25 — riesgo por edad:          edad / 90.0            → mayor edad, más grave si se infecta
  γ = 0.25 — vulnerabilidad estrato:   (7 - estrato) / 6.0   → estrato 1 da 1.0; estrato 6 da 0.17
  δ = 0.15 — grado normalizado:        grado(v) / max(grado) → ¿cuántos puede contagiar directo?

Todos los factores están normalizados a [0.0, 1.0].
```

#### Por qué estos pesos

```
Factores de red     (α + δ = 0.35 + 0.15 = 0.50): cuánto puede PROPAGAR el virus
Factores personales (β + γ = 0.25 + 0.25 = 0.50): cuánto SUFRIRÍA el individuo

→ Peso igual a "cortar cadenas de contagio" y "proteger a los más vulnerables"
```

#### Ejemplo comparativo

Dos personas susceptibles en la misma red:

```
Persona A: CB_norm=0.8, edad=30, estrato=1, grado_norm=0.5
  score = 0.35×0.8 + 0.25×(30/90) + 0.25×((7-1)/6) + 0.15×0.5
        = 0.28   + 0.083          + 0.25            + 0.075
        = 0.688

Persona B: CB_norm=0.2, edad=75, estrato=2, grado_norm=0.3
  score = 0.35×0.2 + 0.25×(75/90) + 0.25×((7-2)/6) + 0.15×0.3
        = 0.07   + 0.208          + 0.208           + 0.045
        = 0.531
```

Híbrida prioriza A porque su posición en la red (betweenness y grado altos) tiene más impacto sistémico, aunque B sea mayor y más vulnerable individualmente.

---

## 5. Comparación completa de estrategias

| Estrategia | Base teórica | Complejidad | Qué información usa del grafo | Fortaleza principal | Limitación |
|------------|-------------|-------------|-------------------------------|--------------------| ------------|
| Aleatoria | Ninguna | O(N) | Ninguna | Referencia neutral, rapidísima | Ignora completamente la topología |
| Hubs | Grado (local) | O(N log N) | Grado saliente de cada nodo | Muy efectiva con hubs pronunciados | No detecta puentes inter-comunidad |
| Betweenness | Intermediación (global) | O(N²) | Todos los caminos mínimos | Detecta puentes críticos inter-comunidad | La más costosa; no usa perfiles individuales |
| Comunidades | Densidad intra-grupo | O(N + M) | Aristas internas por estrato | Corta contagio intra-grupo, muy rápida | No bloquea puentes entre comunidades |
| BFSPonderado | Prob. máxima de ruta | O(K × N log N) | Pesos de aristas desde K focos | Más barata que Betweenness; usa probabilidades reales | Solo evalúa K focos hipotéticos |
| Híbrida | Score compuesto | O(N²) | Betweenness + grado + atributos | Más completa: combina red + perfil individual | Costosa; pesos α,β,γ,δ son heurísticos |

---

## 6. Propiedades de la red generada

La red producida por `GeneradorPoblacion` tiene propiedades de redes sociales reales que explican por qué las estrategias avanzadas superan a la aleatoria:

- **Estructura de comunidades**: los estratos forman grupos densos internamente y poco conectados entre sí. `VacunacionComunidades` y `VacunacionBetweenness` aprovechan esto.
- **Hubs**: los nodos comunitarios (mercado, iglesia) tienen grado mucho mayor que el promedio. `VacunacionHubs` y `VacunacionBFSPonderado` los priorizan.
- **Efecto small-world**: el diámetro de la red es pequeño gracias a las conexiones de largo alcance, aunque la mayoría de contactos son locales.
- **Pesos heterogéneos**: cada arista tiene su propia `probContagio` calibrada por el perfil del nodo origen. `BFSPonderado` e `Híbrida` los usan directamente.

En una red homogénea (todos los nodos con el mismo grado y pesos iguales), la aleatoria y las demás darían el mismo resultado. La estructura y los pesos de esta red **contienen información**, y cada estrategia la extrae de una forma diferente.

---

## 7. Pesos dinámicos adaptativos — NPI, reacción local y fatiga social (v11)

### El concepto: tres capas de dinamismo en los pesos

A partir de v11 el peso efectivo de cada arista en cada turno es el producto de tres factores independientes:

```
probEfectiva(u→v, t) = clamp( probBase(u→v) × factorEventos(t) × vigilancia(v,t) × fatiga(t),
                               0.05, 0.95 )
```

### Capa 1 — Eventos NPI (GestorEventos)

Tres intervenciones de salud pública que reducen el factor global cuando la epidemia supera umbrales:

| Evento | Umbral | Factor | Factor acumulado (si se disparan los tres) |
|--------|--------|--------|--------------------------------------------|
| `ALERTA_LEVE` | 30% infectados | × 0.80 | × 0.80 |
| `CUARENTENA` | 50% infectados | × 0.50 | × 0.40 |
| `LOCKDOWN` | 70% infectados | × 0.20 | × 0.08 |

Cada evento se dispara exactamente una vez (flag `yaDisparado`). `GestorEventos` **ya no modifica aristas directamente**: acumula los factores en `factorAcumuladoEventos` y se lo pasa al ajustador.

### Capa 2 — Reacción local (AjustadorPesosAdaptativo)

Cada nodo v reduce la probabilidad de sus **aristas entrantes** según cuántos de sus vecinos que le apuntan están infectados:

```
vigilancia(v) = 1 − 0.60 × (infectadosEntrantes(v) / totalEntrantes(v))

vigilancia = 1.00  si 0% de vecinos infectados (sin reducción)
vigilancia = 0.70  si 50% de vecinos infectados (−30%)
vigilancia = 0.40  si 100% de vecinos infectados (−60%, máximo)
```

Esta vigilancia se aplica a la arista u→v: si v está rodeado de infectados, la probabilidad de que CUALQUIERA de ellos lo contagie baja. El factor es completamente **local e independiente por nodo**: un nodo con vecinos sanos no se beneficia ni se perjudica de lo que pasa en otra parte de la red.

### Capa 3 — Fatiga social (AjustadorPesosAdaptativo)

Si la epidemia lleva 5 o más turnos consecutivos sin crecer (I(t) ≤ I(t−1)), la población se relaja y los pesos aumentan gradualmente hasta un máximo del 30%:

```
turnosDecreciendo < 5:    fatiga = 1.00  (sin efecto)
turnosDecreciendo = 5:    fatiga = 1.00  (primer turno de umbral)
turnosDecreciendo = 10:   fatiga = 1.15  (+15%)
turnosDecreciendo = 15:   fatiga = 1.30  (+30%, máximo)
```

Cuando I(t) vuelve a crecer, `turnosDecreciendo` se reinicia y `fatiga = 1.00`. Este factor modela el **efecto memoria**: el confinamiento aún tiene efecto (factorEventos < 1), pero las personas se vuelven más laxas conforme el peligro parece menor.

### Flujo de actualización por turno

```
Turno t:
  1. ModeloSIRV.simularTurno(red)
        ↓ usa probContagio efectiva del turno anterior
  2. GestorEventos.evaluar(red, t)
        ↓ si se supera un umbral: factorAcumuladoEventos × factorEvento
  3. historial.add(conteo)
        ↓ registra I(t) para el cálculo de fatiga
  4. AjustadorPesosAdaptativo.ajustar(red, factorEventos, historial)
        ↓ para cada arista:
           a. computarVigilancia(red)   → stats de infectados entrantes por nodo
           b. computarFatiga(historial) → compara I(t) vs I(t−1), actualiza contador
           c. c.setProbContagio(base × factorEventos × vigilanciaDestino × fatiga)
```

### Ejemplo numérico compuesto

```
Arista P001 → P043 en el turno 22:
  probContagioBase = 0.55   (trabajador de salud, estrato 2, 45 años)
  factorEventos    = 0.40   (ALERTA_LEVE × CUARENTENA ya disparados)
  vigilancia(P043) = 0.52   (60% de los vecinos de P043 están infectados →
                              1 − 0.60 × 0.80 = 0.52)
  fatiga(22)       = 1.00   (la epidemia sigue creciendo, sin fatiga)

  probEfectiva = clamp(0.55 × 0.40 × 0.52 × 1.00, 0.05, 0.95)
               = clamp(0.114, 0.05, 0.95) = 0.114

Turno 35 (epidemia en declive, 8 turnos bajando):
  factorEventos    = 0.08   (los tres NPI activos)
  vigilancia(P043) = 1.00   (P043 ya no tiene vecinos infectados)
  fatiga(35)       = 1.09   (8 turnos > umbral 5 → 3/10 × 30% = +9%)

  probEfectiva = clamp(0.55 × 0.08 × 1.00 × 1.09, 0.05, 0.95)
               = clamp(0.048, 0.05, 0.95) = 0.05  ← clamp al mínimo
```

### Efecto observable en las curvas

```
Infectados
    │
 70%┤            ╭── sin dinámica adaptativa: bajada suave y uniforme
    │           ╱
 50%┤ ─ ─ ─ ─ ─                         ╭── con fatiga: rebote tardío al relajarse
    │      ALERTA  CUAREN  LOCKDOWN      │
 30%┤          ↓      ↓       ↓          │
    │           ╲                        │
    │            ╲  con vigilancia:      ╱
    │             ╲ bajada más rápida  ─╯  (la gente se cuida más en el pico)
    └──────────────────────────────────────► Turno
```

### Cómo se observa en la aplicación

- **Alertas en pantalla.** Cuando un evento NPI se dispara o la fatiga cambia de estado, `GraficoSimulacion` muestra un banner de color sobre el grafo durante unos segundos (naranja/rojo para NPI, verde al iniciar la fatiga, dorado al restaurarse la precaución). Esto hace visible el momento exacto en que el modelo modifica el comportamiento de la red.
- **Historial de pesos (solo individual/comparativo).** `VentanaResultados` incluye una pestaña "Historial de pesos" con una tabla nodo×turno: cada celda es el promedio de `probContagio` de las aristas salientes del nodo en ese turno. Permite seguir cómo, p. ej., bajo la estrategia `BETWEENNESS`, los pesos caen al dispararse la cuarentena y vuelven a subir cuando la fatiga social entra en juego. En el comparativo hay una sub-pestaña por cada una de las 6 estrategias.

---

## 8. Cómo se conecta el grafo — construcción visual paso a paso

Esta sección muestra visualmente cómo la red pasa de un conjunto de nodos aislados a un grafo completamente conectado con estructura social real.

> **Modo interactivo (v10).** Lo que esta sección describe en ASCII ahora puede
> verse en vivo desde la aplicación: el modo **"Construcción visual de la red"**
> (4ª opción del menú) abre una ventana GraphStream que revela la red fase por
> fase y arista por arista, con un color distinto por fase (nodos, familia,
> vecindario, hubs, long-range, puente). Implementado en
> `presentation/VisualizadorConstruccionRed` sobre
> `GeneradorPoblacion.generarConFases(N, semilla, listener)`.

### Estado inicial — N nodos aislados

Al terminar la Fase 1, el grafo tiene N nodos sin ninguna arista:

```
P001   P002   P003   P004   P005   P006   P007   P008   P009   P010   P011   P012
 ○      ○      ○      ○      ○      ○      ○      ○      ○      ○      ○      ○
```

---

### Fase 2 — Clusters familiares (cliques de alta probabilidad)

Los nodos se agrupan al azar en familias de 4–7 miembros. Dentro de cada familia **todos los pares se conectan bidireccionalmente** con `probBase` 0.30–0.40. El resultado es una **clique dirigida**: cada nodo tiene aristas hacia todos los demás del grupo.

```
  Familia 1 (estrato 2)         Familia 2 (estrato 1)         Familia 3 (estrato 3)
  ┌──────────────────┐          ┌──────────────────┐          ┌──────────────────┐
  │  P001 ←──→ P002  │          │  P005 ←──→ P006  │          │  P009 ←──→ P010  │
  │   ↑ ╲     ╱ ↑   │          │   ↑ ╲     ╱ ↑   │          │   ↑ ╲     ╱ ↑   │
  │   │  ╲   ╱  │   │          │   │  ╲   ╱  │   │          │   │  ╲   ╱  │   │
  │  P003 ←──→ P004  │          │  P007 ←──→ P008  │          │  P011 ←──→ P012  │
  └──────────────────┘          └──────────────────┘          └──────────────────┘
  (aristas ~0.35, densas)       (aristas ~0.38, densas)       (aristas ~0.32, densas)
```

Estas cliques son los "núcleos" de contagio intra-familiar. El virus se propaga muy rápido dentro de una familia una vez que entra.

---

### Fase 3 — Vecindarios (puentes entre familias del mismo estrato)

Las familias de estratos similares se conectan entre sí con aristas moderadas (0.10–0.18). No todos los miembros se conectan, solo 1–3 pares por vecindario. La probabilidad de conexión decrece con la distancia:

```
distancia 1 (familias adyacentes): 100% de conexión
distancia 2:                         55% de conexión
distancia 3:                         30% de conexión
```

Visualmente, después de la Fase 3:

```
  Familia 1 (estrato 2)         Familia 2 (estrato 2)         Familia 3 (estrato 3)
  ┌──────────────────┐          ┌──────────────────┐          ┌──────────────────┐
  │  P001 ←──→ P002  │          │  P005 ←──→ P006  │          │  P009 ←──→ P010  │
  │   ↕ ╲     ╱ ↕   │          │   ↕ ╲     ╱ ↕   │          │   ↕ ╲     ╱ ↕   │
  │  P003 ←──→ P004  │          │  P007 ←──→ P008  │          │  P011 ←──→ P012  │
  └────────┬─────────┘          └──┬──────────┬────┘          └──────────────────┘
           │                       │          │
           └──── P001↔P007 ────────┘          └──── P006↔P011 (prob 55%, estrato diff≤2)
                 (aristas ~0.14)
```

Estos puentes entre familias son los que detecta `VacunacionBetweenness`: P001 y P007 tienen betweenness alto porque son el camino por el que la Familia 1 puede llegar a la Familia 2.

---

### Fase 4 — Hubs comunitarios (nodos de grado muy alto)

Se seleccionan 2–3 nodos al azar que pasan a ser "hubs". Cada hub se conecta con `N/10` personas de distintas familias y estratos, con `probBase` moderada (0.08–0.15):

```
Antes de Fase 4:
  Fam1──Fam2    Fam3──Fam4    Fam5──Fam6    (grupos desconectados entre sí)

Después de Fase 4 — Hub H1 conecta a todos:

                        H1 (hub: mercado)
                      / │ │ │ │ │ │ │ \
                P001 P003 P006 P008 P010 P012 P014 P016 P018
                (un miembro de cada familia, distintos estratos)

  Resultado: H1 tiene grado ~N/10. Es el nodo que VacunacionHubs priorizaría.
```

---

### Fase 4.5 — Conexiones de largo alcance (efecto small-world)

Se agregan `N/15` aristas aleatorias entre nodos de cualquier parte de la red, con `probBase` baja (0.05–0.13). Estos son los "conocidos lejanos": compañeros de trabajo de otra ciudad, contactos de redes sociales digitales.

```
Antes (sin long-range):
  Fam1 ─── Fam2 ─── Fam3 ─── Fam4 ─── Fam5 ─── Fam6
  (para ir de Fam1 a Fam6 hay que pasar por todas)
  Diámetro ≈ 5 saltos

Después (con long-range):
  Fam1 ─── Fam2 ─── Fam3 ─── Fam4 ─── Fam5 ─── Fam6
   │                  │                  │
   └──────── salto ───┘                  │
             largo                       │
             alcance ────────────────────┘

  Diámetro ≈ 2–3 saltos (small-world)
```

Efecto clave: el virus puede llegar de cualquier comunidad a cualquier otra en muy pocos pasos, aunque la mayoría de contactos sean locales.

---

### Fase 5 — Garantizar conectividad

Se hace un **BFS no dirigido** sobre el grafo completo para detectar componentes conexas (grupos de nodos que no tienen ningún camino entre sí):

```
BFS desde P001:
  visita P001 → P002, P003 (familia 1)
  visita P002 → P004, P007 (vecindario)
  ...continúa hasta agotar todos los alcanzables

Si quedan nodos no visitados → son un componente separado:
  Componente principal: [P001...P089]
  Componente huérfano:  [P090...P095]  ← nunca recibió ninguna arista

  Solución: agregar arista bidireccional P090 ↔ P045 (prob 0.10–0.20)
            Ahora P090 puede alcanzar a P001 en 2 pasos.
```

Esto garantiza que la epidemia pueda alcanzar en teoría a cualquier nodo desde cualquier otro.

---

### Estado final — red completa

Después de las 5 fases, el grafo tiene la siguiente estructura en capas:

```
CAPA 1 — Aristas familiares (densas, prob 0.30–0.40):
  Cliques de 4–7 nodos. Propagación rápida intra-familiar.

CAPA 2 — Aristas vecinales (moderadas, prob 0.10–0.18):
  Puentes entre familias del mismo estrato. Propagación lenta inter-familiar.
  → Estos son los nodos de alto betweenness.

CAPA 3 — Hubs comunitarios (cruzadas, prob 0.08–0.15):
  Nodos de grado alto que conectan distintos clusters.
  → Estos son los nodos que VacunacionHubs prioriza.

CAPA 4 — Conexiones long-range (débiles, prob 0.05–0.13):
  Puentes de largo alcance que reducen el diámetro de la red.
  → Efecto small-world: pocos pasos entre cualquier par de nodos.
```

Esquema global del grafo final con 12 nodos de ejemplo:

```
    [Fam1: estrato 2]          [Fam2: estrato 2]
    P001 ←──→ P002             P005 ←──→ P006
       ↕╲    ╱↕     vecindario    ↕╲    ╱↕
    P003 ←──→ P004 ←─────────→ P007 ←──→ P008
                  ╲                         ╱
                   ╲    Hub H1 (mercado)   ╱
                    ╰──────── H1 ──────────╯
                   ╱                         ╲
    P009 ←──→ P010 ←─────────→ P011 ←──→ P012
       ↕╲    ╱↕     vecindario    ↕╲    ╱↕
    P011 ←──→ P012             P013 ←──→ P014
    [Fam3: estrato 3]          [Fam4: estrato 3]

    P001 ←────── long-range ──────────────→ P013
    (arista débil, simula conocido lejano)
```

### Propiedades emergentes de esta construcción

| Propiedad | Por qué aparece | Qué algoritmo la aprovecha |
|-----------|-----------------|---------------------------|
| Comunidades densas | Fases 2 y 3 | `VacunacionComunidades` (densidad interna) |
| Nodos puente | Fase 3 (pocas aristas inter-familia) | `VacunacionBetweenness` |
| Hubs de grado alto | Fase 4 | `VacunacionHubs`, `VacunacionBFSPonderado` |
| Diámetro pequeño | Fase 4.5 | Explica por qué el virus llega rápido a toda la red |
| Grafo conexo | Fase 5 | Sin esto, algunos nodos nunca se infectarían |

---

## 9. Flujo completo del sistema

```
GeneradorPoblacion.generar(N, semilla)
    ↓ (o CargadorRedCSV para red desde archivo)
RedSocial — grafo dirigido ponderado
    ↓
red.setearPacienteCero(5% de N, random)    → infecta los MISMOS nodos en las 6 estrategias
    ↓
  [los pacientes cero pasan a INFECTADO y quedan fuera del pool susceptible]
    ↓
VacunacionService.aplicar(estrategia)        → vacuna el 20% de los SUSCEPTIBLES restantes
    │   ├── ALEATORIA     → VacunacionAleatoria.vacunar(red)
    │   ├── HUBS          → VacunacionHubs.vacunar(red)
    │   ├── BETWEENNESS   → VacunacionBetweenness.vacunar(red)
    │   ├── COMUNIDADES   → VacunacionComunidades.vacunar(red)
    │   ├── BFS_PONDERADO → VacunacionBFSPonderado.vacunar(red)
    │   └── HIBRIDA       → VacunacionHibrida.vacunar(red)
    ↓
  [20% de susceptibles pasan a VACUNADO]
    ↓
SimulacionService.ejecutar(red, config, grafico)
    │
    ├── ajustador.ajustar() inicial  ← vigilancia del paciente cero antes del turno 1
    │
    └── loop hasta I = 0 o turno máximo:
          ├── ModeloSIRV.simularTurno(red)
          │     ├── Recolectar nuevosInfectados (aristas INFECTADO→SUSCEPTIBLE, usa probContagio efectiva)
          │     ├── Recolectar nuevosRecuperados (diasInfectado >= diasRecuperacion)
          │     └── Aplicar cambios de estado (sincronía — todos al final)
          │
          ├── GestorEventos.evaluar(red, turno)
          │     └── Si % infectados supera umbral: factorAcumuladoEventos × factor
          │
          ├── Registrar {S, I, R, V} del turno en historial
          │
          └── AjustadorPesosAdaptativo.ajustar(red, factorEventos, historial)
                ├── computarVigilancia(red)   → stats infectadosEntrantes por nodo
                ├── computarFatiga(historial) → I(t) vs I(t−1), actualiza turnosDecreciendo
                └── para cada arista: setProbContagio(base × eventos × vigilancia × fatiga)
    ↓
ResultadoSimulacionDto
    ↓
ExportadorResultados / GeneradorReportePDF (8 págs.) / GeneradorReporteLotePDF / VentanaComparativaTabs

Nota: el PDF individual termina con 8 páginas (la última es el glosario de métricas).
El PDF de lotes agrega también la página de glosario (con la variable "Victorias" incluida).
```
