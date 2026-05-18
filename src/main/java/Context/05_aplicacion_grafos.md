# Aplicación Profunda de Grafos y Algoritmos en el Simulador

Este documento profundiza en las bases matemáticas, la complejidad algorítmica y las decisiones de diseño a nivel de estructuras de datos que soportan el simulador epidemiológico. La solución no solo aplica teoría de grafos básica, sino que integra modelos probabilísticos (Stochastic Block Model, Watts-Strogatz) y algoritmos avanzados de centralidad.

---

## 1. Fundamento Matemático y Estructuras de Datos

### 1.1 Modelo Matemático del Grafo
El sistema modela la población como un grafo dirigido ponderado definido como **$G = (V, E, W)$**:
*   **$V$ (Vértices):** Cada vértice $v \in V$ es un objeto `Persona`. A diferencia de grafos simples donde el nodo es un escalar, aquí cada nodo contiene un **vector de características demográficas**: $C_v = (\text{edad}, \text{estrato}, \text{ocupación}, \text{estado\_SIRV})$.
*   **$E$ (Aristas):** El conjunto de aristas dirigidas $E \subseteq V \times V$, donde $(u, v) \in E$ indica que existe una vía de transmisión de la enfermedad del nodo $u$ al nodo $v$.
*   **$W$ (Pesos):** Una función de mapeo $W: E \to [0.05, 0.95]$ que define la **probabilidad de transmisión** estocástica por cada contacto.

### 1.2 Estructura de Datos subyacente
Para implementar $G$, se descartó la Matriz de Adyacencia ($V \times V$) por su extrema ineficiencia espacial. En una red de $N = 10,000$ personas, una matriz requeriría 100 millones de entradas de memoria, siendo el 99.9% ceros absolutos, ya que las redes humanas son **altamente dispersas** (Sparse Graphs).

En su lugar, se implementó una **Lista de Adyacencia** mapeada mediante Tablas Hash:
```java
HashMap<Persona, List<Contacto>> adyacencia;
```
*   **Complejidad Espacial:** $O(|V| + |E|)$.
*   **Complejidad Temporal de Búsqueda de Vecinos:** $O(1)$ para hallar el nodo en el `HashMap` y $O(k)$ para iterar sus contactos, siendo $k$ el grado saliente del nodo (usualmente $k \ll |V|$).

---

## 2. Algoritmos de Construcción de la Red Topológica

La construcción del grafo no es aleatoria uniforme. Implementa una versión algorítmica adaptada del **Stochastic Block Model (SBM)** combinado con heurísticas de **Watts-Strogatz (Small-World)**, ejecutada en 6 fases:

1.  **Generación de Nodos (Monte Carlo):** Se instancian los $|V|$ nodos aplicando el método de Monte Carlo sobre las funciones de distribución de probabilidad acumulada (CDF) poblacionales extraídas del DANE, garantizando una representatividad demográfica estadísticamente rigurosa.
2.  **Formación de Cliques Locales (Familias):** Se particionan los nodos en subconjuntos disjuntos de tamaño 4 a 7. Por cada subconjunto, se genera un **subgrafo completo** (o clique) insertando aristas bidireccionales de alto peso.
3.  **Interconexión Homofílica (Comunidades):** Para modelar "vecindarios", se conectan nodos de diferentes cliques que comparten el mismo atributo `estrato`. La probabilidad de crear una arista $P(u, v)$ decae exponencialmente según la "distancia social" percibida, agrupando fuertemente a las comunidades.
4.  **Inyección de Nodos de Alta Centralidad (Hubs):** Se seleccionan heurísticamente nodos específicos para actuar como "Hubs Comunitarios". Se fuerza artificialmente un incremento masivo en su grado de entrada y salida ($indegree$ y $outdegree$), conectándolos transversalmente a múltiples comunidades.
5.  **Reconexión Aleatoria (Efecto Small-World):** Siguiendo a Watts-Strogatz, se añaden aristas con probabilidad $\beta$ entre nodos de estratos opuestos. Esto reduce drásticamente el **Diámetro del Grafo** (la distancia del camino más corto máximo) y la **Longitud Media del Camino**, permitiendo que la epidemia salte globalmente.
6.  **Validación de Componente Conexo Único (Algoritmo BFS):** Se ejecuta un algoritmo estándar de Búsqueda en Anchura (BFS). Si se detectan componentes fuertemente disconexos (subgrafos aislados donde el virus no podría llegar), se inyectan "aristas puente" artificiales para unificar todo en un solo gran componente conexo.

---

## 3. Algoritmo Estocástico de Propagación (Modelo SIRV)

El modelo de infección opera como un autómata celular estocástico sobre el grafo. La actualización es **sincrónica**: el estado de la red en el turno $t+1$ depende exclusivamente del estado en el turno $t$.

Para calcular la probabilidad de que un nodo susceptible $v$ se infecte en el turno $t+1$, se evalúa la probabilidad complementaria de *no* ser infectado por ninguno de sus vecinos infectados $u$:

$$ \mathbb{P}(v \text{ se infecta}) = 1 - \prod_{u \in N_{inf}(v)} (1 - W(u,v)) $$

**Modificación Dinámica de Pesos (GestorEventos):**
Durante la simulación, el grafo es mutable. Cuando $|I| / |V| > \text{Umbral}$ (ej. 50% infectados), se aplica una Cuarentena. Computacionalmente, esto invoca una operación map-reduce sobre el conjunto total de aristas $E$, multiplicando escalarmente todo $W(e)$ por un factor de atenuación $\lambda$ (ej. 0.5), ralentizando la propagación y "alargando" algorítmicamente las distancias epidemiológicas.

---

## 4. Profundización Algorítmica: Estrategias de Vacunación

El problema de vacunación se traduce a: *Dado un presupuesto de eliminación de nodos $B = 0.2|V|$, ¿qué subconjunto de vértices $V' \subset V$ debe ser removido (pasado a estado Vacunado) para minimizar la propagación esperada?*

### 4.1 Estrategia de Hubs (Centralidad de Grado Saliente)
*   **Enfoque matemático:** Identificar vértices donde el grado de salida $\deg_{out}(v)$ es máximo. Un alto $\deg_{out}$ implica ser una super-fuente ("Super-Spreader").
*   **Algoritmo:** Iterar $V$, calcular el tamaño de la lista de adyacencia de cada nodo, almacenar en una estructura y aplicar un algoritmo de ordenamiento rápido (QuickSort/TimSort).
*   **Complejidad:** $O(|V| \log |V|)$.

### 4.2 Estrategia Betweenness (Centralidad de Intermediación)
*   **Enfoque matemático:** Mide cuántas veces un nodo $v$ sirve de puente en el camino más corto entre otros dos nodos $s$ y $t$. La fórmula de Freeman es:
    $$ C_B(v) = \sum_{s \neq v \neq t} \frac{\sigma_{st}(v)}{\sigma_{st}} $$
    donde $\sigma_{st}$ es el total de caminos más cortos entre $s$ y $t$, y $\sigma_{st}(v)$ es la cantidad de esos caminos que atraviesan $v$.
*   **Algoritmo implementado (Algoritmo de Brandes):** Calcular esto ingenuamente toma $O(|V|^3)$. Se usa la optimización de Brandes que consta de dos pasos por cada nodo fuente $s$:
    1. **Fase Forward (BFS Modificado):** Se descubre el árbol de caminos más cortos desde $s$, contando los caminos hacia cada nodo.
    2. **Fase Backward (Acumulación):** Se retrocede en el árbol de dependencias, acumulando el "crédito" de intermediación para los nodos puente.
*   **Complejidad:** $O(|V|(|V| + |E|))$. Extremadamente costoso, pero estructuralmente infalible para cortar puentes entre comunidades distintas.

### 4.3 Estrategia de Densidad (Comunidades)
*   **Enfoque matemático:** Busca subgrafos inducidos $G_S = (V_S, E_S)$ con alta **Densidad de Grafo**:
    $$ \text{Densidad} = \frac{|E_S|}{|V_S|(|V_S| - 1)} $$
*   **Algoritmo:** Agrupa los nodos $|V|$ por el atributo demográfico (Estrato) asumiendo que representan clústeres fuertemente conexos. Recorre las aristas y cuenta cuántas tienen origen y destino dentro de la misma partición. Luego, concentra el presupuesto de vacunas $B$ en la partición (comunidad) con mayor densidad calculada, priorizando internamente por grado.
*   **Complejidad:** $O(|V| + |E|)$. Altamente eficiente y corta directamente el crecimiento intra-comunitario.

### 4.4 Estrategia de Rutas Críticas (BFS Ponderado / Dijkstra Multiplicativo)
*   **Enfoque matemático:** Los algoritmos estándar de caminos mínimos suman distancias. Aquí, la probabilidad de que una infección viaje por un camino $P = e_1, e_2, \dots, e_k$ es el **producto** de sus probabilidades:
    $$ \mathbb{P}(P) = \prod_{i=1}^k W(e_i) $$
    Para usar Dijkstra clásicamente, se tendría que maximizar productos, lo cual es equivalente a minimizar la suma de logaritmos negativos: $\sum -\ln(W(e_i))$.
*   **Algoritmo (Modificación Estocástica):** Se omite la conversión a logaritmos y se ajusta el criterio de relajación. Usando una **Cola de Prioridad (Max-Heap)**, se evalúa:
    `si (probAcumulada[u] * W(u,v) > probAcumulada[v]) -> actualizar y encolar(v)`
*   **Aplicación:** Se aplica desde los "Hubs" asumiendo que son los pacientes cero más probables. Rastrea qué nodos aparecen más frecuentemente en los caminos probabilísticos más fuertes y los vacuna.

### 4.5 Estrategia Multicriterio Híbrida
*   **Enfoque matemático:** Los algoritmos anteriores evalúan solo la topología del grafo. La estrategia híbrida calcula una **Función Objetivo Multivariable**:
    $$ S(v) = \alpha \cdot \widetilde{C_B}(v) + \beta \cdot \text{RiesgoEdad}(v) + \gamma \cdot \text{RiesgoEstrato}(v) + \delta \cdot \widetilde{\deg}(v) $$
*   **Algoritmo:** 
    1. Pre-computa la centralidad de intermediación $C_B$ (Brandes) y la centralidad de Grado $\deg$.
    2. Normaliza todos los valores en el rango $[0, 1]$.
    3. Construye una `PriorityQueue` (Max-Heap) basada en la función objetivo $S(v)$ para extraer los $\lfloor 0.2 \cdot |V| \rfloor$ elementos en tiempo $\mathcal{O}(|V| \log |V|)$.
*   **Razonamiento:** Produce un "corte" en la red que es matemáticamente óptimo tanto desde la conectividad topológica como desde el impacto biomédico individual.
