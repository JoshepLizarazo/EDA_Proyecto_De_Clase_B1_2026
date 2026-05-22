# Planificación Técnica — Arquitectura y Responsabilidades

## Tecnologías

| Tecnología | Uso |
|---|---|
| Java 17+ | Lenguaje principal |
| GraphStream 2.x | Visualización del grafo en tiempo real |
| Maven | Gestión de dependencias |
| CSV plano | Carga opcional de grafos predefinidos (rubric) |

### Dependencia Maven — GraphStream
```xml
<dependency>
    <groupId>org.graphstream</groupId>
    <artifactId>gs-core</artifactId>
    <version>2.0</version>
</dependency>
<dependency>
    <groupId>org.graphstream</groupId>
    <artifactId>gs-ui-swing</artifactId>
    <version>2.0</version>
</dependency>
```

---

## Arquitectura por capas

El proyecto sigue una arquitectura de 4 capas inspirada en Domain-Driven Design adaptada a la complejidad del curso. Cada capa tiene una responsabilidad única y solo depende de la capa inmediatamente inferior.

```
presentation   →  application   →   domain   →   infrastructure
  (UI)            (servicios)      (núcleo)       (técnico)
```

---

## Estructura completa del proyecto

```
EpidemiaSimulador/
├── pom.xml
├── README.md
├── INSTRUCCIONES_EJECUCION.md
│
├── data/
│   ├── personas_red1.csv
│   ├── contactos_red1.csv
│   ├── personas_red2.csv
│   └── contactos_red2.csv
│
└── src/main/java/co/uis/epidemia/
    ├── presentation/
    │   ├── Main.java
    │   ├── ConsolaMenu.java
    │   ├── GraficoSimulacion.java
    │   └── PanelEstadisticas.java
    │
    ├── application/
    │   ├── service/
    │   │   ├── SimulacionService.java
    │   │   └── VacunacionService.java
    │   ├── dto/
    │   │   ├── ResultadoSimulacionDto.java
    │   │   └── ConfiguracionDto.java
    │   └── command/
    │       ├── IniciarSimulacionCommand.java
    │       └── AplicarVacunacionCommand.java
    │
    ├── domain/
    │   ├── model/
    │   │   ├── Persona.java
    │   │   ├── Contacto.java
    │   │   ├── RedSocial.java
    │   │   └── EventoEpidemiologico.java
    │   ├── value/
    │   │   ├── EstadoSIRV.java
    │   │   ├── TipoEvento.java
    │   │   └── EstrategiaVacunacion.java
    │   └── algoritmo/
    │       ├── ModeloSIRV.java
    │       ├── GestorEventos.java
    │       ├── BFSPonderado.java
    │       ├── VacunacionAleatoria.java
    │       ├── VacunacionHubs.java
    │       ├── VacunacionBetweenness.java
    │       ├── VacunacionComunidades.java
    │       └── VacunacionHibrida.java
    │
    └── infrastructure/
        ├── persistence/
        │   ├── CargadorRedCSV.java
        │   ├── ExportadorResultados.java
        │   └── RedMemoryRepository.java
        └── util/
            ├── GeneradorPoblacion.java
            └── CalculadorEstadisticas.java
```

---

## CAPA 1 — Presentation

### `Main.java`
- Punto de entrada del programa (`public static void main`).
- Instancia `ConsolaMenu` y lanza la aplicación.
- No contiene lógica de negocio.

### `ConsolaMenu.java`
- Maneja la interacción con el usuario por consola (Scanner).
- Muestra el menú principal: elegir tamaño de red, estrategia de vacunación, número de turnos, semilla aleatoria.
- Construye el `ConfiguracionDto` con las elecciones del usuario.
- Llama a `SimulacionService` para ejecutar.
- Muestra los resultados textuales al finalizar.

### `GraficoSimulacion.java`
- Envuelve la API de GraphStream para representar el grafo visualmente.
- Crea un nodo visual por cada `Persona` y una arista visual por cada `Contacto`.
- Actualiza el color de cada nodo en cada turno según su `EstadoSIRV`.
- Actualiza el grosor de aristas cuando `GestorEventos` modifica los pesos.
- Métodos principales: `inicializar(RedSocial)`, `actualizarTurno(RedSocial, int turno)`, `cerrar()`.

### `PanelEstadisticas.java`
- Muestra al final de la simulación las curvas S/I/R/V por turno usando GraphStream Charts o Swing básico.
- Recibe la lista de `ResultadoSimulacionDto` de cada estrategia y los grafica en un panel comparativo.
- Muestra: curva de infectados por turno, barras de pico máximo, tabla resumen de ganador.

---

## CAPA 2 — Application

### `SimulacionService.java`
- Orquesta la ejecución completa de una simulación.
- Flujo interno por turno:
  1. Llama a `ModeloSIRV.simularTurno(RedSocial)`.
  2. Llama a `GestorEventos.evaluar(RedSocial, turnoActual)`.
  3. Actualiza `GraficoSimulacion`.
  4. Registra el conteo S/I/R/V en el historial.
  5. Verifica condición de parada (sin infectados activos).
- Al terminar construye y retorna `ResultadoSimulacionDto`.

### `VacunacionService.java`
- Recibe la estrategia elegida (`EstrategiaVacunacion`) y la `RedSocial`.
- Delega al algoritmo correspondiente:
  - `ALEATORIA`   → `VacunacionAleatoria`
  - `HUBS`        → `VacunacionHubs`
  - `BETWEENNESS` → `VacunacionBetweenness`
  - `COMUNIDADES` → `VacunacionComunidades`
  - `HIBRIDA`     → `VacunacionHibrida`
- Marca el 20% de nodos susceptibles como `VACUNADO` antes de iniciar la simulación.

### `ResultadoSimulacionDto.java`
- Objeto de transferencia de datos con los resultados de una simulación.
- Campos: `estrategia`, `picoPorTurno[]`, `picoMaximoInfectados`, `duracionBrote`, `totalRecuperados`, `totalVacunados`.

### `ConfiguracionDto.java`
- Objeto que encapsula la configuración elegida por el usuario.
- Campos: `tamanoRed` (N personas), `turnosMaximos`, `estrategia`, `semillaAleatoria`, `probInfeccionBase`, `diasRecuperacion`.

### `IniciarSimulacionCommand.java`
- Encapsula la acción de iniciar una simulación completa.
- Recibe `ConfiguracionDto`, genera la red, aplica vacunación y ejecuta `SimulacionService`.
- Permite ejecutar las 4 estrategias en secuencia para el análisis comparativo.

### `AplicarVacunacionCommand.java`
- Encapsula la acción de vacunar el 20% según la estrategia indicada.
- Llamado por `IniciarSimulacionCommand` antes de arrancar el modelo SIRV.

---

## CAPA 3 — Domain

### `Persona.java`
- Representa un nodo del grafo.
- Atributos:
  - `id` (String, único)
  - `nombre` (String)
  - `edad` (int) — influye en el peso de sus aristas
  - `estrato` (int, 1–6) — influye en cantidad de contactos
  - `ocupacion` (String) — influye en tipo de conexiones
  - `estado` (EstadoSIRV)
  - `diasInfectado` (int) — contador para recuperación
- Métodos: getters/setters, `toString()`, `equals()` por `id`, `hashCode()` por `id`.

### `Contacto.java`
- Representa una arista dirigida del grafo.
- Atributos:
  - `origen` (Persona)
  - `destino` (Persona)
  - `probContagio` (double, 0.0–1.0) — peso de la arista
- El peso se calcula al crear la arista con base en los atributos de origen y destino.
- Métodos: getters, `setProbContagio()` (usado por `GestorEventos`).

### `RedSocial.java`
- El grafo principal: `HashMap<Persona, List<Contacto>>`.
- Métodos principales:
  - `agregarPersona(Persona)` — agrega nodo
  - `agregarContacto(Persona origen, Persona destino, double peso)` — agrega arista
  - `getContactos(Persona)` — lista de aristas salientes
  - `getTodasLasPersonas()` — todos los nodos
  - `getTodosLosContactos()` — todas las aristas
  - `getGrado(Persona)` — número de conexiones
  - `setearPacienteCero(int cantidad)` — infecta N nodos aleatorios al inicio

### `EventoEpidemiologico.java`
- Representa un evento de salud pública que modifica el grafo.
- Atributos:
  - `tipo` (TipoEvento)
  - `umbral` (double) — porcentaje de infectados que lo activa
  - `factorMultiplicador` (double) — modifica todos los `probContagio`
  - `yaDisparado` (boolean) — evita que se active dos veces
  - `nombre` (String) — descripción del evento
- Métodos: `getters`, `disparar()`, `estaDisparado()`.

### `EstadoSIRV.java` (enum)
```
SUSCEPTIBLE   // puede infectarse
INFECTADO     // tiene la enfermedad y puede contagiar
RECUPERADO    // inmune, ya no contagia ni se infecta
VACUNADO      // inmune desde el inicio de la simulación
```

### `TipoEvento.java` (enum)
```
ALERTA_LEVE   // 30% infectados — distanciamiento social
CUARENTENA    // 50% infectados — restricción de movilidad
LOCKDOWN      // 70% infectados — confinamiento total
```

### `EstrategiaVacunacion.java` (enum)
```
ALEATORIA     // selección al azar del 20%
HUBS          // 20% con mayor grado de conexiones
BETWEENNESS   // 20% con mayor centralidad de intermediación
COMUNIDADES   // 20% desde comunidades (estratos) con mayor densidad interna
HIBRIDA       // 20% con mayor score compuesto (aporte propio)
```

---

### `ModeloSIRV.java`
Implementa la propagación de la epidemia turno a turno.

**Responsabilidades por turno:**
1. Recorre todos los nodos `INFECTADO`.
2. Por cada vecino `SUSCEPTIBLE`, genera un número aleatorio entre 0 y 1.
3. Si el número < `probContagio` de la arista, el vecino pasa a `INFECTADO`.
4. Incrementa `diasInfectado` de cada infectado.
5. Si `diasInfectado >= diasRecuperacion`, el nodo pasa a `RECUPERADO`.
6. Retorna conteo actual `{S, I, R, V}`.

**Parámetros configurables:**
- `probInfeccionBase` — probabilidad base (modificada por atributos del nodo)
- `diasRecuperacion` — turnos hasta recuperación (por defecto 7)

### `GestorEventos.java`
Evalúa en cada turno si se debe disparar un evento automático.

**Responsabilidades:**
1. Calcula `porcentajeInfectados = infectados / totalPoblacion`.
2. Recorre la lista de eventos no disparados.
3. Si `porcentajeInfectados >= umbral` del evento, llama a `disparar()`.
4. Al disparar: recorre **todas las aristas** de `RedSocial` y multiplica `probContagio` × `factorMultiplicador`.
5. Notifica a `GraficoSimulacion` para actualizar el grosor de aristas.

### `BFSPonderado.java`
Rastreo del camino de mayor probabilidad de contagio entre dos nodos.

**Uso:** identificar la ruta más probable por la que viajó la infección desde el paciente cero hasta un nodo dado. Se usa en el análisis post-simulación para visualizar la "cadena de contagio".

**Estructura interna:** cola de prioridad (`PriorityQueue`) donde la prioridad es la probabilidad acumulada del camino (mayor probabilidad = mayor prioridad).

### `VacunacionAleatoria.java`
- Obtiene la lista de todos los nodos `SUSCEPTIBLE`.
- Baraja la lista aleatoriamente con una semilla.
- Toma el primer 20% y los marca como `VACUNADO`.

### `VacunacionHubs.java`
- Obtiene el grado de cada nodo (`RedSocial.getGrado(persona)`).
- Ordena todos los nodos de mayor a menor grado.
- Toma el primer 20% y los marca como `VACUNADO`.

### `VacunacionBetweenness.java`
- Calcula la centralidad de intermediación para cada nodo.
- Definición: número de caminos más cortos entre cualquier par de nodos que pasan por ese nodo.
- Implementación: BFS desde cada nodo para contar caminos que lo atraviesan.
- Ordena de mayor a menor y toma el 20%.

### `VacunacionComunidades.java` ✓ (implementado)
- Agrupa los nodos susceptibles por estrato socioeconómico (proxy de comunidad colombiana).
- Calcula la densidad interna de cada grupo: `aristasInternas / (n × (n-1))`.
- Ordena los grupos de mayor a menor densidad.
- Vacuna desde el grupo más denso, priorizando dentro de cada grupo por grado saliente.
- Métodos: `vacunar(RedSocial)`, `calcularDensidadesPorEstrato(RedSocial)`.
- Complejidad: O(N + M) — más eficiente que Betweenness e Híbrida.
- Opera a nivel de **grupo**, no de individuo. Complementa a Híbrida en el análisis comparativo.

### `VacunacionHibrida.java` ★ (aporte propio)
Calcula un score compuesto por persona y vacuna los de mayor score.

**Fórmula:**
```
score(v) = α · centralidad(v)  +  β · riesgo_edad(v)
         + γ · vulnerabilidad_estrato(v)  +  δ · grado(v)
```

**Parámetros sugeridos:** α = 0.35, β = 0.25, γ = 0.25, δ = 0.15

**Funciones auxiliares:**
- `riesgo_edad(v)` — normalizado: edad / 90. Adultos mayores = score alto.
- `vulnerabilidad_estrato(v)` — normalizado: (7 - estrato) / 6. Estrato 1 = score alto.
- `centralidad(v)` — betweenness normalizado.
- `grado(v)` — grado normalizado: grado(v) / gradoMaximo.

Usa `PriorityQueue<Persona>` ordenada por score descendente. Toma el 20% superior.

---

## CAPA 4 — Infrastructure

### `GeneradorPoblacion.java`
Genera una `RedSocial` aleatoria con distribuciones colombianas reales.

**Parámetros de entrada:** número de personas N, semilla aleatoria.

**Proceso de generación:**
1. Genera N personas con edad, estrato y ocupación según distribuciones reales.
2. Forma clusters familiares de 4–7 personas y las conecta fuertemente.
3. Conecta familias dentro del mismo barrio (estrato similar) con menor peso.
4. Genera hubs comunitarios (mercado, iglesia, transporte) que conectan clusters distintos.
5. Calcula el `probContagio` de cada arista con base en los atributos de los nodos conectados.

**Fórmula del peso de arista:**
```
probContagio = probBase × factorEdad(origen) × factorEstrato(origen) × factorOcupacion(origen)
```

Donde:
- `factorEdad` = 1.0 + (edad / 100) — mayor edad, más vulnerable
- `factorEstrato` = 1.0 + (0.1 × (6 - estrato)) — menor estrato, más exposición
- `factorOcupacion` = depende de la ocupación (salud: 1.4, estudiante: 1.2, informal: 1.3, empleado: 1.0, jubilado: 0.8)
- El resultado se normaliza entre 0.05 y 0.95.

### `CargadorRedCSV.java`
Carga una `RedSocial` desde archivos CSV (requerido por el rubric).

**Formato `personas_red1.csv`:**
```
id,nombre,edad,estrato,ocupacion
P001,Juan Rueda,34,2,informal
P002,María López,67,1,jubilado
...
```

**Formato `contactos_red1.csv`:**
```
origen,destino,probContagio
P001,P002,0.45
P002,P015,0.30
...
```

### `ExportadorResultados.java`
Guarda el comparativo final en un archivo `.txt` o `.csv`.

**Contenido exportado:** estrategia, pico máximo, duración, total recuperados, ganador.

### `RedMemoryRepository.java`
Implementación de la interfaz `RedRepository`. Guarda la `RedSocial` activa en memoria durante la ejecución. Permite que distintos servicios accedan al mismo grafo sin pasarlo como parámetro en cada llamada.

### `CalculadorEstadisticas.java`
Calcula métricas de análisis al final de cada simulación.

**Métricas calculadas:**
- **Pico máximo de infectados** — valor máximo de I en todo el historial.
- **Turno del pico** — en qué turno ocurrió el máximo.
- **Duración del brote** — número de turnos hasta que I = 0.
- **Total de afectados** — suma final de Recuperados (los que enfermaron).
- **Porcentaje de contención** — `(N - totalAfectados) / N × 100`.
- **R0 estimado** — promedio de nuevos infectados por nodo infectado en los primeros 3 turnos.

---

## Archivos de datos — formato CSV

### `personas_red1.csv` — Red pequeña (~80 nodos, barrio)
Representa un barrio de estrato 1–2 en Bucaramanga con alta densidad familiar.

### `personas_red2.csv` — Red grande (~300 nodos, ciudad)
Representa una comunidad más diversa con mezcla de estratos, ocupaciones variadas y múltiples vecindarios conectados.

Los CSV son los **dos casos de prueba** exigidos por el rubric para demostrar el comportamiento en distintos tamaños y topologías de red.

---

## División de trabajo por integrante

### Persona 1 — Dominio y algoritmos
- `domain/model/` — Persona, Contacto, RedSocial, EventoEpidemiologico
- `domain/value/` — EstadoSIRV, TipoEvento, EstrategiaVacunacion
- `domain/algoritmo/` — ModeloSIRV, GestorEventos, BFSPonderado
- `domain/algoritmo/` — VacunacionAleatoria, VacunacionHubs, VacunacionBetweenness, VacunacionComunidades ✓, VacunacionHibrida

### Persona 2 — Infraestructura y servicios
- `infrastructure/util/` — GeneradorPoblacion, CalculadorEstadisticas
- `infrastructure/persistence/` — CargadorRedCSV, ExportadorResultados, RedMemoryRepository
- `application/service/` — SimulacionService, VacunacionService
- Archivos `data/*.csv`

### Persona 3 — Presentación y comandos
- `presentation/` — Main, ConsolaMenu, GraficoSimulacion, PanelEstadisticas
- `application/dto/` — ResultadoSimulacionDto, ConfiguracionDto
- `application/command/` — IniciarSimulacionCommand, AplicarVacunacionCommand
- `INSTRUCCIONES_EJECUCION.md`

---

## Flujo completo de ejecución

```
1. Main.java
   └── ConsolaMenu.java
         ├── Recibe configuración del usuario (N, turnos, estrategia, semilla)
         └── Construye ConfiguracionDto

2. IniciarSimulacionCommand
   ├── GeneradorPoblacion.java  →  crea RedSocial con N personas colombianas
   ├── CargadorRedCSV.java      →  alternativa: carga desde archivo data/
   ├── VacunacionService.java   →  marca 20% según estrategia elegida
   └── SimulacionService.java   →  ejecuta turnos:
         ├── ModeloSIRV.java        →  propaga infección
         ├── GestorEventos.java     →  ajusta pesos si hay umbral superado
         └── GraficoSimulacion.java →  actualiza colores en pantalla

3. CalculadorEstadisticas.java  →  calcula métricas finales

4. PanelEstadisticas.java       →  muestra comparativo de las 4 estrategias

5. ExportadorResultados.java    →  guarda resultado en archivo
```

---

## Cronograma sugerido (1 semana)

| Día | Actividad |
|---|---|
| Día 1 | ✓ `domain/model/` + `domain/value/` — modelos base y enums · `Persona` factores de riesgo · `GeneradorPoblacion.calcularProbContagio` · `VacunacionComunidades` |
| Día 2 | `GeneradorPoblacion.generar()` (5 fases) + `ModeloSIRV` — grafo funcional + propagación |
| Día 3 | Las 5 estrategias de vacunación (Aleatoria, Hubs, Betweenness, Híbrida) + `GestorEventos` |
| Día 4 | `SimulacionService` + `VacunacionService` (5 estrategias) + `ConsolaMenu` |
| Día 5 | `GraficoSimulacion` con GraphStream + `PanelEstadisticas` |
| Día 6 | `CargadorRedCSV` + `CalculadorEstadisticas` + pruebas con los 2 CSV |
| Día 7 | Integración completa + `BFSPonderado` + `ExportadorResultados` + análisis comparativo 5 estrategias + informe |
