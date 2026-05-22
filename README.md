# Simulador de Propagación de Epidemia sobre Redes Sociales

Proyecto académico de la asignatura **Estructuras de Datos y Análisis de Algoritmos (22955)** — Universidad Industrial de Santander (UIS), Facultad de Ingeniería de Sistemas e Informática, grupo B1-C1.

**Docente:** Laura Viviana Galvis Carreño | **Peso en la nota:** 20% del curso

---

## Demostración

[![Demo en YouTube](https://img.youtube.com/vi/yjJLEvcspzw/0.jpg)](https://youtu.be/yjJLEvcspzw)

[Ver demo completa en YouTube](https://youtu.be/yjJLEvcspzw)

---

## 1. ¿De qué trata el proyecto?

Es un simulador en **Java 17** que modela cómo una enfermedad se propaga a través de una red social representada como un **grafo dirigido y ponderado**, donde:

- Cada **nodo** es una `Persona` con atributos demográficos colombianos reales (edad, estrato socioeconómico, ocupación).
- Cada **arista** es un `Contacto` cuyo peso (`probContagio ∈ [0.05, 0.95]`) depende del perfil de riesgo del nodo origen.
- La propagación se modela con el esquema epidemiológico **SIRV** (Susceptible → Infectado → Recuperado / Vacunado).

El objetivo central es **comparar seis estrategias de vacunación** bajo la restricción de que solo se puede vacunar al **20% de la población**, y determinar cuantitativamente cuál minimiza el impacto del brote.

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

### 2.4 Eventos automáticos por umbral

`GestorEventos` simula intervenciones de salud pública. Cuando se cruza un umbral de infectados, multiplica el peso de **todas** las aristas por un factor de atenuación (los factores son acumulativos):

| Evento | Umbral θ | Factor λ | Significado |
|---|---|---|---|
| `ALERTA_LEVE` | 30% | × 0.80 | Distanciamiento social voluntario |
| `CUARENTENA` | 50% | × 0.50 | Restricción de movilidad |
| `LOCKDOWN` | 70% | × 0.20 | Confinamiento total |

Cada evento se dispara una sola vez (flag `yaDisparado`).

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
- En modo comparativo, una `VentanaComparativaTabs` aloja las 6 simulaciones en pestañas separadas (`JTabbedPane`).
- `PanelEstadisticas` dibuja curvas SIRV en ASCII al cierre.
- `GeneradorReportePDF` (JFreeChart + OpenPDF) produce un PDF de 7 páginas con: portada, tabla resumen con zebra y ganador destacado, veredicto cuantitativo, curva I(t) comparativa, curvas SIRV por estrategia, barras por métrica con paleta unificada y desglose del score apilado.

### 2.7 BFS Ponderado — cadena de contagio

`BFSPonderado` usa una `PriorityQueue` (max-heap) ordenada por probabilidad acumulada (`máx Π w(eᵢ)`) para encontrar la ruta más probable de contagio desde un foco. Esta misma idea se reutiliza como criterio de vacunación en `VacunacionBFSPonderado`: identifica los nodos intermedios en las rutas más críticas desde K=5 focos hipotéticos.

---

## 3. Interfaz gráfica (v7)

Desde la versión 7, el programa abre por defecto una **interfaz Swing oscura** con **FlatDarkLaf**. La consola sigue disponible como fallback.

### Modos de arranque

```bash
mvn exec:java                              # Abre la UI Swing (por defecto)
mvn exec:java -Dexec.args="--consola"     # Fuerza el menú por consola
```

En entornos headless (CI, servidores sin pantalla), el programa detecta automáticamente `GraphicsEnvironment.isHeadless()` y cae a la consola sin errores.

### Ventanas de la UI

| Ventana | Descripción |
|---|---|
| `VentanaMenuPrincipal` | Ventana raíz. Banner azul con título, modo individual/comparativo y botones Continuar/Salir. |
| `VentanaConfiguracion` | Modal con formulario completo: tamaño de red, estrategia, turnos, días de recuperación, visualización y carga desde CSV. |
| `VentanaResultados` | Tabla de métricas con fila ganadora resaltada, ranking con score compuesto, curva I(t) con JFreeChart, y botones de exportación. |
| `DialogoProgreso` | Diálogo modal con barra de progreso indeterminada mientras el `SwingWorker` ejecuta la simulación en background. |

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
    │   ├── VentanaMenuPrincipal.java       ← Ventana raíz Swing (FlatDarkLaf)
    │   ├── VentanaConfiguracion.java       ← Formulario modal de configuración
    │   ├── VentanaResultados.java          ← Tabla de métricas + curva I(t) + exportación
    │   ├── DialogoProgreso.java            ← Barra de progreso durante la simulación
    │   ├── ConsolaMenu.java                ← Menú por consola (fallback / --consola)
    │   ├── GraficoSimulacion.java          ← Vista GraphStream (standalone + embebida)
    │   ├── VentanaComparativaTabs.java     ← JTabbedPane con las 6 simulaciones
    │   └── PanelEstadisticas.java          ← Curvas SIRV en ASCII
    │
    ├── application/               ← CAPA 2 — Servicios
    │   ├── service/
    │   │   ├── SimulacionService.java      ← Loop de turnos + historial
    │   │   └── VacunacionService.java      ← Switch por estrategia
    │   ├── dto/
    │   │   ├── ConfiguracionDto.java       ← Entrada del usuario
    │   │   └── ResultadoSimulacionDto.java ← Métricas finales
    │   └── command/
    │       ├── IniciarSimulacionCommand.java
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
        │   ├── CargadorRedCSV.java         ← Carga red desde CSV
        │   ├── ExportadorResultados.java   ← Exporta a .txt / .csv
        │   ├── GeneradorReportePDF.java    ← PDF de 7 páginas (JFreeChart + OpenPDF)
        │   └── RedMemoryRepository.java    ← Repositorio en memoria
        └── util/
            ├── GeneradorPoblacion.java     ← Genera red con patrones colombianos (6 fases)
            ├── CalculadorEstadisticas.java ← Métricas finales
            └── AnalisisComparativo.java    ← Score compuesto y ranking
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

1. Seleccionar modo: **individual** (una estrategia) o **comparativo** (las 6 a la vez).
2. Configurar en el formulario: tamaño de red, estrategia, turnos, días de recuperación y si se desea visualización GraphStream.
3. Opcionalmente cargar una red desde CSV (`data/`).
4. Al finalizar el comparativo, exportar los resultados en **TXT**, **PDF** o ambos.

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
  ├── VacunacionService                  ← vacuna el 20% según estrategia
  └── SimulacionService                  ← loop de turnos:
        ├── ModeloSIRV                   ← propaga infección (sincrónica)
        ├── GestorEventos                ← dispara eventos por umbral
        └── GraficoSimulacion            ← actualiza la vista (si activa)

Al terminar (modo comparativo):
  ├── CalculadorEstadisticas → métricas finales
  ├── AnalisisComparativo    → ranking por score compuesto
  ├── VentanaResultados / PanelEstadisticas → tabla + curva I(t) / ASCII
  └── GeneradorReportePDF / ExportadorResultados → PDF + TXT
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

---

## 10. Documentación adicional

La carpeta `src/main/java/Context/` contiene el diseño detallado del proyecto:

- `01_contexto_proyecto.md` — Contexto académico, problema y objetivos.
- `02_planificacion_tecnica.md` — Arquitectura por capas, responsabilidades archivo a archivo.
- `03_modelo_matematico.md` — Formalización del grafo, SIRV, estrategias e hipótesis.
- `04_estructura_creada.md` — Historial de cambios (v1 → v7) y detalle de implementación.
- `05_grafos_y_algoritmos.md` — Explicación técnica completa de los algoritmos y la estructura del grafo.
