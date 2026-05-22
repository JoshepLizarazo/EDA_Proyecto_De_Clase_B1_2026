# Simulador de Propagación de Epidemia sobre Redes Sociales

Proyecto académico de la asignatura **Estructuras de Datos y Análisis de Algoritmos (22955)** — Universidad Industrial de Santander (UIS), Facultad de Ingeniería de Sistemas e Informática, grupo B1-C1.

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

### 2.2 Generación de la población (patrones colombianos)

`GeneradorPoblacion` crea la red en cinco fases imitando la realidad social colombiana:

1. Distribución de edades según pirámide DANE (mayor concentración 15–35).
2. Distribución de estratos: 60% estratos 1–2, 30% estrato 3, 10% estratos 4–6.
3. Clusters familiares de 4–7 personas con aristas fuertes.
4. Vecindarios que conectan familias del mismo estrato con aristas moderadas.
5. Hubs comunitarios (mercado, iglesia, transporte) que conectan clusters distintos.

Alternativamente, se puede cargar una red predefinida desde CSV (`data/personas_redN.csv` + `data/contactos_redN.csv`).

### 2.3 Modelo SIRV — propagación por turnos

Cada turno discreto, `ModeloSIRV.simularTurno(RedSocial)`:

1. Recorre los nodos en estado `INFECTADO`.
2. Para cada vecino `SUSCEPTIBLE`, genera `r ∈ [0,1)`; si `r < probContagio`, lo infecta.
3. Incrementa `diasInfectado` y, si supera `diasRecuperacion`, pasa a `RECUPERADO`.
4. Retorna el conteo `{S, I, R, V}` del turno.

### 2.4 Eventos automáticos por umbral

`GestorEventos` simula intervenciones de salud pública. Cuando se cruza un umbral de infectados, multiplica el peso de **todas** las aristas por un factor de atenuación:

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
- `GeneradorReportePDF` (JFreeChart + OpenPDF) produce un PDF de 7 páginas con: portada, tabla resumen, veredicto, curva I(t) comparativa, curvas SIRV por estrategia, barras por métrica y desglose del score.

### 2.7 BFS Ponderado — cadena de contagio

`BFSPonderado` usa una `PriorityQueue` ordenada por probabilidad acumulada (`máx Π w(eᵢ)` ≡ `máx Σ log w(eᵢ)`) para reconstruir, post-simulación, la ruta más probable por la que viajó la infección desde el paciente cero. Esta misma idea se reutiliza como criterio de vacunación en `VacunacionBFSPonderado`.

---

## 3. Arquitectura por capas

El proyecto sigue una arquitectura de 4 capas inspirada en Domain-Driven Design. Cada capa solo depende de la capa inmediatamente inferior:

```
presentation  →  application  →  domain  →  infrastructure
   (UI)          (servicios)    (núcleo)     (técnico)
```

| Capa | Responsabilidad |
|---|---|
| **presentation** | Interacción con el usuario (consola, ventanas Swing/GraphStream) |
| **application** | Orquesta casos de uso (servicios, DTOs, commands) |
| **domain** | Núcleo del problema: modelos, value objects y algoritmos puros |
| **infrastructure** | Detalles técnicos: persistencia CSV/PDF, generación, estadísticas |

---

## 4. Estructura de carpetas

```
Trabas_y_Grafos/
├── pom.xml                        ← Maven (Java 17, GraphStream, JFreeChart, OpenPDF)
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
    │   └── 04_estructura_creada.md
    │
    ├── presentation/              ← CAPA 1 — UI
    │   ├── Main.java                       ← Punto de entrada
    │   ├── ConsolaMenu.java                ← Menú por consola (Scanner)
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
    │       ├── ModeloSIRV.java             ← Propagación turno a turno
    │       ├── GestorEventos.java          ← Eventos por umbral
    │       ├── BFSPonderado.java           ← Camino de máxima probabilidad
    │       ├── VacunacionAleatoria.java
    │       ├── VacunacionHubs.java
    │       ├── VacunacionBetweenness.java
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
            ├── GeneradorPoblacion.java     ← Genera red con patrones colombianos
            ├── CalculadorEstadisticas.java ← Métricas finales
            └── AnalisisComparativo.java    ← Score compuesto y ranking
```

---

## 5. Tecnologías y dependencias

| Tecnología | Uso |
|---|---|
| **Java 17** | Lenguaje principal |
| **Maven** | Gestión del build y dependencias |
| **GraphStream 2.0** (`gs-core`, `gs-ui-swing`) | Visualización del grafo en tiempo real |
| **JFreeChart 1.5.4** | Gráficos vectoriales para el reporte |
| **OpenPDF 1.3.34** | Exportación del reporte a PDF |

---

## 6. Cómo ejecutar

```bash
# Compilar
mvn compile

# Ejecutar
mvn exec:java
```

El menú por consola permite:

1. Elegir el tamaño de la red (o cargar un CSV de `data/`).
2. Seleccionar una estrategia individual **o** lanzar el comparativo de las 6 estrategias.
3. Activar/desactivar la visualización con GraphStream.
4. Al finalizar el comparativo, exportar los resultados en **TXT**, **PDF** o ambos.

---

## 7. Flujo completo de ejecución

```
Main
  └── ConsolaMenu                          ← recibe configuración (N, turnos, estrategia)
        └── IniciarSimulacionCommand
              ├── GeneradorPoblacion        ← genera RedSocial colombiana
              │   ─ó─
              │   CargadorRedCSV            ← alternativa: red desde data/*.csv
              ├── VacunacionService         ← vacuna el 20% según estrategia
              └── SimulacionService         ← loop de turnos:
                    ├── ModeloSIRV          ← propaga infección
                    ├── GestorEventos       ← dispara eventos por umbral
                    └── GraficoSimulacion   ← actualiza la vista (si activa)

  Al terminar (modo comparativo):
    ├── CalculadorEstadisticas → métricas finales
    ├── AnalisisComparativo    → ranking por score compuesto
    ├── PanelEstadisticas      → curvas SIRV en ASCII
    └── GeneradorReportePDF / ExportadorResultados → PDF + TXT
```

---

## 8. Documentación adicional

La carpeta `src/main/java/Context/` contiene el diseño detallado del proyecto:

- `01_contexto_proyecto.md` — Contexto académico, problema y objetivos.
- `02_planificacion_tecnica.md` — Arquitectura por capas, responsabilidades archivo a archivo.
- `03_modelo_matematico.md` — Formalización del grafo, SIRV, estrategias e hipótesis.
- `04_estructura_creada.md` — Historial de cambios (v1 → v6) y detalle de implementación.
