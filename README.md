# Simulador de Propagación de Epidemia sobre Redes Sociales

Proyecto académico de la asignatura **Estructuras de Datos y Análisis de Algoritmos (22955)** — Universidad Industrial de Santander (UIS), Facultad de Ingeniería de Sistemas e Informática, grupo B1-C1.

**Docente:** Laura Viviana Galvis Carreño | **Peso en la nota:** 20% del curso

**Integrantes:** Joshep Jared Lizarazo Montero (2250150) | Juan Esteban Barajas Mantilla (2250183) | Juan Pablo Rueda Angarita (2250160)

---

## Demostración

[![Demo en YouTube](https://img.youtube.com/vi/WMGwWu-O2zY/0.jpg)](https://youtu.be/WMGwWu-O2zY)

[Ver demo completa en YouTube](https://youtu.be/WMGwWu-O2zY)

---

## 1. ¿De qué trata el proyecto?

Es un simulador en **Java 17** que modela cómo una enfermedad se propaga a través de una red social representada como un **grafo dirigido y ponderado**, donde:

- Cada **nodo** es una `Persona` con atributos demográficos colombianos reales (edad, estrato socioeconómico, ocupación).
- Cada **arista** es un `Contacto` cuyo peso (`probContagio ∈ [0.05, 0.95]`) depende del perfil de riesgo del nodo origen.
- La propagación se modela con el esquema epidemiológico **SIRV** (Susceptible → Infectado → Recuperado / Vacunado).

El objetivo central es **comparar seis estrategias de vacunación** bajo la restricción de que solo se puede vacunar al **20% de la población**, y determinar cuantitativamente cuál minimiza el impacto del brote.

Para que la comparación sea **justa**, el brote arranca con un **5% de infectados iniciales fijos** (los mismos pacientes cero para las seis estrategias) y luego cada estrategia vacuna el 20% de los susceptibles restantes — así ninguna estrategia parte de una condición inicial distinta. Además del modo individual y del comparativo sobre una misma red, existe un **modo por lotes** que corre N grafos distintos por estrategia y promedia los resultados, y un **modo de construcción visual** que muestra paso a paso cómo se arma la red social (ver §3).

### Estrategias de vacunación implementadas

| # | Estrategia | Nivel | Complejidad | Idea clave |
|---|---|---|---|---|
| 1 | `ALEATORIA` | Individuo | O(N) | Línea base, sin información de la red |
| 2 | `HUBS` | Individuo | O(N log N) | Vacuna los nodos de mayor grado |
| 3 | `BETWEENNESS` | Individuo | O(N·(N+M)) | Vacuna los puentes inter-comunidad |
| 4 | `COMUNIDADES` | Grupo | O(N+M) | Prioriza grupos de mayor densidad interna |
| 5 | `HIBRIDA` ★ | Individuo | O(N·(N+M)) | **Aporte propio** — score compuesto (estructura + demografía) |
| 6 | `BFS_PONDERADO` | Individuo | O(K·N·(N+M) log N) | Vacuna nodos en las rutas de máxima probabilidad de contagio |

★ La estrategia híbrida es la contribución original del equipo y combina centralidad, edad, vulnerabilidad por estrato y grado en un score:

```
score(v) = 0.35·CB_norm(v) + 0.25·riesgo_edad(v) + 0.25·vulnerabilidad_estrato(v) + 0.15·deg_norm(v)
```

---

## 2. ¿Cómo funciona?

### 2.1 Modelo del grafo

La red se representa internamente con una **lista de adyacencia**:

```java
HashMap<Persona, List<Contacto>>
```

El peso de cada arista `(u → v)` se calcula con los factores de riesgo del origen:

```
probContagio(u → v) = probBase × fEdad(u) × fEstrato(u) × fOcupacion(u)
                      → normalizado a [0.05, 0.95]
```

donde:

- `fEdad = 1.0 + edad / 100` (mayor edad ⇒ más vulnerable)
- `fEstrato = 1.0 + 0.1·(6 − estrato)` (estrato bajo ⇒ más exposición)
- `fOcupacion`: salud 1.4, informal 1.3, estudiante 1.2, empleado 1.0, jubilado 0.8

### 2.2 Generación de la población (patrones colombianos) — 6 fases

`GeneradorPoblacion` crea la red en seis fases imitando la realidad social colombiana:

1. **Distribución demográfica DANE**: edades (mayor concentración 15–35), estratos (60% estratos 1–2, 30% estrato 3, 10% estratos 4–6), ocupación correlacionada con edad.
2. **Clusters familiares**: cliques de 4–7 personas con aristas muy fuertes (prob 0.30–0.40).
3. **Vecindarios**: conexiones moderadas (0.10–0.18) entre familias del mismo estrato.
4. **Hubs comunitarios**: 2–3 nodos de alta conectividad (mercado, iglesia, transporte) que conectan clusters de distintos estratos.
5. **Conexiones de largo alcance** (efecto small-world): N/15 aristas aleatorias débiles (0.05–0.13) que reducen el diámetro de la red.
6. **Garantizar conectividad**: BFS no dirigido para detectar componentes aisladas; se agregan aristas puente si las hay.

Alternativamente, se puede cargar una red predefinida desde CSV (`data/personas_redN.csv` + `data/contactos_redN.csv`).

### 2.3 Modelo SIRV — propagación por turnos

Cada turno discreto, `ModeloSIRV.simularTurno(RedSocial)` opera de forma **sincrónica** (todos los cambios de estado se aplican al final del turno, no durante):

1. Recolecta `nuevosInfectados`: para cada vecino `SUSCEPTIBLE` de un nodo `INFECTADO`, genera `r ∈ [0,1)`; si `r < probContagio`, lo agrega al conjunto.
2. Recolecta `nuevosRecuperados`: nodos `INFECTADO` cuyo `diasInfectado >= diasRecuperacion`.
3. Aplica todos los cambios de estado simultáneamente.
4. Retorna el conteo `{S, I, R, V}` del turno.

### 2.4 Pesos dinámicos adaptativos

A partir de v11, el peso efectivo de cada arista se recalcula en cada turno como composición de tres factores:

```
probEfectiva(u→v) = clamp( probBase(u→v) × factorEventos × vigilancia(v) × fatiga, 0.05, 0.95 )
```

**NPI — Eventos por umbral (`GestorEventos`):** cuando el porcentaje de infectados supera un umbral, se multiplica el factor global de forma acumulativa (los tres factores juntos = × 0.08):

| Evento | Umbral θ | Factor λ | Significado |
|---|---|---|---|
| `ALERTA_LEVE` | 30% | × 0.80 | Distanciamiento social voluntario |
| `CUARENTENA` | 50% | × 0.50 | Restricción de movilidad |
| `LOCKDOWN` | 70% | × 0.20 | Confinamiento total |

Cada evento se dispara una sola vez (flag `yaDisparado`). `GestorEventos` rastrea el factor acumulado y se lo pasa al ajustador; ya no modifica las aristas directamente.

**Reacción local (`AjustadorPesosAdaptativo`):** cada nodo v reduce la prob de sus aristas entrantes según la fracción de sus vecinos que están infectados:

```
vigilancia(v) = 1 − 0.60 × (infectadosEntrantes(v) / totalEntrantes(v))
```

Si todos los vecinos de v están infectados, la reducción es del 60%. Si ninguno lo está, no hay efecto. Este factor se recalcula cada turno desde cero.

**Fatiga social (`AjustadorPesosAdaptativo`):** si I(t) lleva 5 o más turnos consecutivos sin crecer, la población se relaja y los pesos aumentan gradualmente (máximo +30%). Se reinicia cuando la epidemia vuelve a crecer.

### 2.5 Análisis cuantitativo del ganador

`AnalisisComparativo` rankea las estrategias con un score compuesto sobre las métricas finales:

```
score = 0.30·norm_inv(picoMaximo) + 0.15·norm_inv(duracion)
      + 0.25·norm_inv(totalAfectados) + 0.20·norm(contencion%)
      + 0.10·norm_inv(R0estimado)
```

`norm_inv` invierte las métricas "menor = mejor" para que el mayor score siempre indique la mejor estrategia.

### 2.6 Visualización

- **GraphStream 2.0** muestra el grafo en tiempo real con nodos coloreados por estado SIRV (azul / rojo / verde / amarillo) y aristas cuyo grosor refleja `probContagio`.
- Bajo el grafo, una **barra de turno** muestra en vivo el turno actual y el conteo `S / I / R / V`, tanto en la ventana individual como en cada pestaña del comparativo.
- Sobre el grafo, un **banner de alertas** aparece cuando se dispara un evento NPI (`ALERTA_LEVE` naranja, `CUARENTENA` naranja oscuro, `LOCKDOWN` rojo) o cuando cambia la fatiga social (verde al iniciar la relajación, dorado al restaurar la precaución por un rebote). Se oculta solo tras unos segundos.
- **Pestaña "Historial de pesos"** en `VentanaResultados` (solo en modos individual y comparativo): una tabla con los nodos en las filas y los turnos en las columnas, donde cada celda es la probabilidad media de contagio (`probContagio`) de las aristas salientes del nodo en ese turno. Permite ver cómo el peso de cada nodo evoluciona turno a turno bajo cada estrategia de vacunación (cada estrategia tiene su propia sub-pestaña en el comparativo).
- En modo comparativo, una `VentanaComparativaTabs` aloja las 6 simulaciones en pestañas separadas (`JTabbedPane`).
- `PanelEstadisticas` dibuja curvas SIRV en ASCII al cierre.
- `GeneradorReportePDF` (JFreeChart + OpenPDF) produce un PDF de **8 páginas** con: portada, tabla resumen con zebra y ganador destacado, veredicto cuantitativo, curva I(t) comparativa, curvas SIRV por estrategia, barras por métrica con paleta unificada, desglose del score apilado, y una página final de **Glosario de métricas** que explica cada variable (S, I, R, V, pico, t-pico, duración, afectados, contención %, R0, score) con su interpretación y qué valores son favorables.
- `GeneradorReporteLotePDF` genera el PDF equivalente para el modo por lotes, con las mismas secciones más la columna de victorias acumuladas y el glosario extendido (incluye la variable "Victorias").

### 2.7 BFS Ponderado — cadena de contagio

`BFSPonderado` usa una `PriorityQueue` (max-heap) ordenada por probabilidad acumulada (`máx Π w(eᵢ)`) para encontrar la ruta más probable de contagio desde un foco. Esta misma idea se reutiliza como criterio de vacunación en `VacunacionBFSPonderado`: identifica los nodos intermedios en las rutas más críticas desde K=5 focos hipotéticos.

---

## 3. Interfaz gráfica (v10)

Desde la versión 7 el programa abre por defecto una **interfaz Swing oscura** con **FlatDarkLaf**. En v10 se incorporó el 4° modo de simulación (construcción visual). La consola sigue disponible como fallback.

### Modos de arranque

```bash
mvn exec:java                              # Abre la UI Swing (por defecto)
mvn exec:java -Dexec.args="--consola"     # Fuerza el menú por consola
```

En entornos headless (CI, servidores sin pantalla), el programa detecta automáticamente `GraphicsEnvironment.isHeadless()` y cae a la consola sin errores.

### Modos de simulación

| Modo | Qué hace |
|---|---|
| **Individual** | Corre una sola estrategia (elegida por el usuario) con visualización GraphStream. |
| **Comparativo** | Corre las 6 estrategias sobre la **misma red** (mismos infectados iniciales) y las muestra en pestañas. |
| **Experimento por lotes** | Corre **N grafos distintos por estrategia** (6 × N grafos independientes), sin animación, y promedia los resultados. |
| **Construcción visual** | Anima la generación de la red fase por fase y arista por arista, con un color distinto por fase: clusters familiares → vecindarios → hubs → conexiones long-range → puentes de conectividad. |

### Ventanas de la UI

| Ventana | Descripción |
|---|---|
| `VentanaMenuPrincipal` | Ventana raíz. Banner azul con título, selección de modo (individual / comparativo / lotes / construcción visual) y botones Continuar/Salir. |
| `VentanaConfiguracion` | Modal con formulario: tamaño de red, estrategia (solo individual), grafos por estrategia (solo lotes), turnos, días de recuperación, visualización y carga desde CSV. |
| `VentanaResultados` | Tabla de métricas con fila ganadora resaltada, ranking con score compuesto, curva I(t) con JFreeChart, **pestaña "Historial de pesos"** (solo individual/comparativo) y botones de exportación. |
| `VentanaResultadosLote` | Resultados del lote: tabla de métricas **promedio** + victorias por estrategia, ranking por score compuesto promedio, curva I(t) promedio y exportación. |
| `DialogoProgreso` | Diálogo modal con barra de progreso (indeterminada en individual/comparativo; determinada "grafo X de N" en el lote). |

### Modo por lotes — comparación promedio + acumulada

En el experimento por lotes, cada estrategia se evalúa sobre **N grafos distintos e independientes** (semilla única por cada par estrategia–corrida; `N` es ahora ilimitado — el tope de 1000 fue removido en v10). Al terminar se reportan dos lecturas complementarias:

- **Promedio**: se promedian las métricas de las N corridas de cada estrategia (pico, duración, afectados, contención, R0) y se rankea con el mismo score compuesto del comparativo.
- **Acumulado (victorias)**: en cada corrida se rankean las estrategias por score y se cuenta una victoria para la mejor; el conteo final estima qué tan seguido cada estrategia resulta la mejor sobre grafos aleatorios.

Como cada estrategia enfrenta una muestra amplia de topologías distintas, el veredicto es estadísticamente más robusto que una sola corrida.

---

## 4. Arquitectura por capas

El proyecto sigue una arquitectura de 4 capas inspirada en Domain-Driven Design:

```
presentation  →  application  →  domain  →  infrastructure
   (UI)          (servicios)    (núcleo)     (técnico)
```

| Capa | Responsabilidad |
|---|---|
| **presentation** | Interacción con el usuario (UI Swing, consola, ventanas GraphStream) |
| **application** | Orquesta casos de uso (servicios, DTOs, commands) |
| **domain** | Núcleo del problema: modelos, value objects y algoritmos puros |
| **infrastructure** | Detalles técnicos: persistencia CSV/PDF, generación, estadísticas |

---

## 5. Estructura de carpetas

```
Trabas_y_Grafos/
├── pom.xml                        ← Maven (Java 17, GraphStream, JFreeChart, OpenPDF, FlatLaf)
├── README.md
├── reporte_simulacion.pdf         ← Reporte PDF de ejemplo
├── resultados_simulacion.txt      ← Resultados de texto de ejemplo
│
├── data/                          ← Casos de prueba (CSV)
│   ├── personas_red1.csv          (~80 nodos — barrio estrato 1-2)
│   ├── contactos_red1.csv
│   ├── personas_red2.csv          (~300 nodos — comunidad mixta)
│   └── contactos_red2.csv
│
└── src/main/java/
    ├── Context/                   ← Documentación de diseño
    │   ├── 01_contexto_proyecto.md
    │   ├── 02_planificacion_tecnica.md
    │   ├── 03_modelo_matematico.md
    │   ├── 04_estructura_creada.md
    │   └── 05_grafos_y_algoritmos.md
    │
    ├── presentation/              ← CAPA 1 — UI
    │   ├── Main.java                       ← Punto de entrada (Swing por defecto, consola con --consola)
    │   ├── ModoSimulacion.java             ← enum {INDIVIDUAL, COMPARATIVO, LOTE, CONSTRUCCION_VISUAL}
    │   ├── VentanaMenuPrincipal.java       ← Ventana raíz Swing (FlatDarkLaf, 4 modos)
    │   ├── VentanaConfiguracion.java       ← Formulario modal de configuración
    │   ├── VentanaResultados.java          ← Tabla de métricas + curva I(t) + exportación
    │   ├── VentanaResultadosLote.java      ← Resultados del lote (promedio + victorias)
    │   ├── DialogoProgreso.java            ← Barra de progreso (indeterminada / por lotes)
    │   ├── ConsolaMenu.java                ← Menú por consola (fallback / --consola)
    │   ├── GraficoSimulacion.java          ← Vista GraphStream + barra de turno (standalone + embebida)
    │   ├── VentanaComparativaTabs.java     ← JTabbedPane con las 6 simulaciones
    │   ├── VisualizadorConstruccionRed.java ← Animación fase a fase de la generación de la red
    │   └── PanelEstadisticas.java          ← Curvas SIRV en ASCII
    │
    ├── application/               ← CAPA 2 — Servicios
    │   ├── service/
    │   │   ├── SimulacionService.java      ← Loop de turnos + historial
    │   │   └── VacunacionService.java      ← Switch por estrategia
    │   ├── dto/
    │   │   ├── ConfiguracionDto.java       ← Entrada del usuario (paciente cero = 5%)
    │   │   ├── ResultadoSimulacionDto.java ← Métricas finales
    │   │   └── ResultadoLoteDto.java       ← Promedios + victorias del experimento por lotes
    │   └── command/
    │       ├── IniciarSimulacionCommand.java  ← ejecutar / ejecutarComparativo / ejecutarLote
    │       └── AplicarVacunacionCommand.java
    │
    ├── domain/                    ← CAPA 3 — Núcleo
    │   ├── model/
    │   │   ├── Persona.java                ← Nodo (id, edad, estrato, ocupación, estado)
    │   │   ├── Contacto.java               ← Arista dirigida (origen, destino, probContagio)
    │   │   ├── RedSocial.java              ← HashMap<Persona, List<Contacto>>
    │   │   └── EventoEpidemiologico.java
    │   ├── value/
    │   │   ├── EstadoSIRV.java             ← enum {SUSCEPTIBLE, INFECTADO, RECUPERADO, VACUNADO}
    │   │   ├── TipoEvento.java             ← enum {ALERTA_LEVE, CUARENTENA, LOCKDOWN}
    │   │   └── EstrategiaVacunacion.java   ← enum con las 6 estrategias
    │   └── algoritmo/
    │       ├── ModeloSIRV.java             ← Propagación turno a turno (sincrónica)
    │       ├── GestorEventos.java          ← Eventos por umbral
    │       ├── BFSPonderado.java           ← Camino de máxima probabilidad (Dijkstra adaptado)
    │       ├── VacunacionAleatoria.java
    │       ├── VacunacionHubs.java
    │       ├── VacunacionBetweenness.java  ← Algoritmo de Brandes
    │       ├── VacunacionComunidades.java
    │       ├── VacunacionHibrida.java       ★ Aporte propio
    │       └── VacunacionBFSPonderado.java
    │
    └── infrastructure/            ← CAPA 4 — Técnico
        ├── persistence/
        │   ├── CargadorRedCSV.java            ← Carga red desde CSV
        │   ├── ExportadorResultados.java      ← Exporta a .txt / .csv
        │   ├── GeneradorReportePDF.java       ← PDF 8 págs. — individual/comparativo (JFreeChart + OpenPDF)
        │   ├── GeneradorReporteLotePDF.java   ← PDF de lotes — promedios + victorias + glosario
        │   └── RedMemoryRepository.java       ← Repositorio en memoria
        └── util/
            ├── GeneradorPoblacion.java     ← Genera red con patrones colombianos (6 fases)
            ├── CalculadorEstadisticas.java ← Métricas finales
            ├── AnalisisComparativo.java    ← Score compuesto y ranking
            └── AgregadorLote.java          ← Promedia las N corridas y cuenta victorias
```

---

## 6. Tecnologías y dependencias

| Tecnología | Uso |
|---|---|
| **Java 17** | Lenguaje principal |
| **Maven** | Gestión del build y dependencias |
| **GraphStream 2.0** (`gs-core`, `gs-ui-swing`) | Visualización del grafo en tiempo real |
| **JFreeChart 1.5.4** | Gráficos vectoriales para el reporte PDF y la UI de resultados |
| **OpenPDF 1.3.34** | Exportación del reporte a PDF |
| **FlatLaf 3.5.4** (`flatlaf`) | Look & Feel oscuro moderno para la interfaz Swing |

---

## 7. Cómo ejecutar

```bash
# Compilar
mvn compile

# Ejecutar (abre UI Swing por defecto)
mvn exec:java

# Ejecutar en modo consola
mvn exec:java -Dexec.args="--consola"
```

### Desde la UI Swing

1. Seleccionar modo: **individual** (una estrategia), **comparativo** (las 6 sobre la misma red), **experimento por lotes** (N grafos distintos por estrategia) o **construcción visual** (animación de la red formándose).
2. Configurar en el formulario: tamaño de red, estrategia (individual), grafos por estrategia (lotes, sin límite), turnos, días de recuperación y si se desea visualización GraphStream.
3. Opcionalmente cargar una red desde CSV (`data/`) — no aplica en modo lotes ni construcción visual.
4. Al finalizar, exportar los resultados en **TXT**, **PDF** o ambos.

### Desde la consola (modo --consola o headless)

1. Elegir el tamaño de la red (o cargar un CSV de `data/`).
2. Seleccionar una estrategia individual **o** lanzar el comparativo de las 6 estrategias.
3. Activar/desactivar la visualización con GraphStream.
4. Al finalizar, exportar los resultados.

---

## 8. Flujo completo de ejecución

```
Main
  ├── VentanaMenuPrincipal (Swing)       ← modo por defecto
  │     └── VentanaConfiguracion (modal)
  │           └── SwingWorker → IniciarSimulacionCommand
  └── ConsolaMenu                        ← con --consola o headless

IniciarSimulacionCommand
  ├── GeneradorPoblacion (6 fases)       ← genera RedSocial colombiana
  │   ─ó ─
  │   CargadorRedCSV                     ← alternativa: red desde data/*.csv
  ├── setearPacienteCero (5%)            ← infecta los mismos nodos antes de vacunar
  ├── VacunacionService                  ← vacuna el 20% de los susceptibles restantes
  └── SimulacionService                  ← loop de turnos:
        ├── ModeloSIRV                   ← propaga infección (sincrónica)
        ├── GestorEventos                ← dispara eventos por umbral
        └── GraficoSimulacion            ← actualiza la vista + barra de turno (si activa)

Al terminar (modo comparativo):
  ├── CalculadorEstadisticas → métricas finales
  ├── AnalisisComparativo    → ranking por score compuesto
  ├── VentanaResultados / PanelEstadisticas → tabla + curva I(t) / ASCII
  └── GeneradorReportePDF / ExportadorResultados → PDF + TXT

Modo experimento por lotes (ejecutarLote):
  └── por cada estrategia × N grafos (semilla única) → IniciarSimulacionCommand.ejecutar
        └── AgregadorLote → promedios por estrategia + victorias por corrida
              └── VentanaResultadosLote → tabla promedio + ranking + curva I(t) promedio
```

---

## 9. Historial de versiones

| Versión | Fecha | Descripción |
|---|---|---|
| v1–v2 | 2026-05-16 | Estructura base, paquetes, modelos, reestructuración de paquetes. |
| v3 | 2026-05-16 | Cálculo de pesos de arista según atributos demográficos. |
| v4 | 2026-05-16 | Estrategia `VacunacionComunidades` completa. |
| v5 | 2026-05-17 | 6ª estrategia BFS Ponderado, reporte PDF con JFreeChart, análisis cuantitativo con score compuesto, mejora visual GraphStream. |
| v6 | 2026-05-17 | Comparativo con `JTabbedPane` de 6 pestañas, modo embebido en `GraficoSimulacion`. |
| v7 | 2026-05-22 | Interfaz Swing completa con FlatDarkLaf (4 ventanas nuevas). Refinamiento visual integral del `GeneradorReportePDF`. Consola disponible vía `--consola` o headless. |
| v8 | 2026-05-23 | Comparación justa: paciente cero (15% de la población) fijado **antes** de vacunar, idéntico para las 6 estrategias. Barra de turno con conteo SIRV bajo el grafo. Nuevo **modo por lotes**: N grafos distintos por estrategia con comparación promedio + acumulada (`AgregadorLote`, `ResultadoLoteDto`, `VentanaResultadosLote`, `ModoSimulacion`). |
| v9 | 2026-05-23 | **Glosario de métricas** en ambos PDFs: página final "Guía de interpretación" que explica cada variable del informe (S, I, R, V, pico, t-pico, duración, afectados, contención %, R0, score compuesto, victorias), qué mide y qué valores son favorables. PDF individual pasa de 7 a 8 páginas. |
| v10 | 2026-05-26 | **Infectados iniciales 15% → 5%** (brote más controlado al inicio). Nuevo **modo "Construcción visual de la red"** (4ª opción): anima la generación fase por fase, arista por arista, con un color distinto por fase. Límite del spinner "Grafos por estrategia" del modo lote removido (antes tope 1000, ahora ilimitado). |
| v11 | 2026-05-27 | **Pesos dinámicos adaptativos**: cada turno los pesos se recomponen como `base × factorEventos × vigilanciaLocal × fatiga`. Nuevo `AjustadorPesosAdaptativo` (reacción local por vecinos infectados + fatiga social si epidemia baja ≥5 turnos). `GestorEventos` deja de modificar aristas directamente. `Contacto` añade `probContagioBase` inmutable. **Alertas en pantalla** (banner superior de `GraficoSimulacion`) al dispararse un evento NPI o un cambio de fatiga. Nueva **pestaña "Historial de pesos"** en `VentanaResultados` (solo individual/comparativo): tabla nodo×turno con la probabilidad media de contagio que evoluciona cada turno. |

---

## 10. Resultados experimentales

Experimento por lotes ejecutado con **N = 300 nodos, 10 000 grafos por estrategia (60 000 simulaciones totales)**, `probBase = 0.20`, 7 días de recuperación, 50 turnos máximos, **15% de pacientes cero** (45 nodos), 20% vacunados. Generado el 2026-05-26.

| Estrategia | Pico | t-Pico | Duración | Afectados | Contención % | R₀ | Victorias |
|---|---|---|---|---|---|---|---|
| **HUBS** ★ | **131** | 6 | **26** | **180** | **40,0 %** | **2,06** | **6 980 / 10 000** |
| BETWEENNESS | 150 | 6 | 27 | 203 | 32,3 % | 2,12 | 1 405 / 10 000 |
| Dijkstra Ponderado | 156 | 6 | 27 | 208 | 30,7 % | 2,15 | 760 / 10 000 |
| HÍBRIDA | 159 | 6 | 27 | 212 | 29,3 % | 2,15 | 547 / 10 000 |
| COMUNIDADES | 169 | 6 | 26 | 216 | 28,0 % | 2,16 | 291 / 10 000 |
| ALEATORIA | 187 | 6 | 24 | 227 | 24,3 % | 2,23 | 17 / 10 000 |

★ **Ganadora: HUBS** — score compuesto promedio 0,355 / 1,000 — 69,8 % de victorias.

### Validación de hipótesis

| H# | Hipótesis | Resultado |
|---|---|---|
| H1 | Hubs supera a Aleatoria en redes con hubs claros. | **Confirmada** — 40 % contención vs 24,3 %; 6 980 vs 17 victorias. |
| H2 | Betweenness es más efectiva en redes con comunidades separadas. | **Confirmada parcialmente** — queda 2.° (1 405 victorias), superada por Hubs. |
| H3 | Híbrida supera a Betweenness con disparidad de estratos. | **No confirmada** — Híbrida queda 4.° (547 victorias). |
| H4 | Eventos por umbral tienen mayor impacto en redes densas (N=300). | **Confirmada** — CUARENTENA y LOCKDOWN se dispararon sistemáticamente antes. |
| H5 | Aleatoria es comparable a Hubs en redes homogéneas. | **Refutada** — la red genera hubs pronunciados que hacen decisiva la diferencia. |
| H6 | Comunidades supera a Aleatoria y Hubs con clusters densos. | **Parcialmente confirmada** — supera a Aleatoria pero queda 5.° en el ranking. |
| H7 | Híbrida supera a Comunidades en redes grandes. | **Confirmada** — Híbrida (4.°, 547) supera a Comunidades (5.°, 291). |

---

## 11. Documentación adicional

La carpeta `src/main/java/Context/` contiene el diseño detallado del proyecto:

- `01_contexto_proyecto.md` — Contexto académico, problema y objetivos.
- `02_planificacion_tecnica.md` — Arquitectura por capas, responsabilidades archivo a archivo.
- `03_modelo_matematico.md` — Formalización del grafo, SIRV, estrategias e hipótesis.
- `04_estructura_creada.md` — Historial de cambios (v1 → v10) y detalle de implementación.
- `05_grafos_y_algoritmos.md` — Explicación técnica completa de los algoritmos y la estructura del grafo.
