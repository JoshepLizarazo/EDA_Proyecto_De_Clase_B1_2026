# Estructura del Proyecto — Cambios Aplicados

**Última actualización:** 2026-05-23

---

## Historial de cambios

| Versión | Fecha | Qué se hizo |
|---|---|---|
| v1 | 2026-05-16 | Estructura base creada. Paquetes, esqueletos y modelos completos. |
| v2 | 2026-05-16 | Reestructuración de paquetes: se eliminó el prefijo `co.uis.epidemia`. |
| v3 | 2026-05-16 | Implementación del cálculo de pesos de arista según atributos demográficos. |
| v4 | 2026-05-16 | Nueva estrategia `VacunacionComunidades` implementada. `EstrategiaVacunacion` y `VacunacionService` actualizados. |
| v5 | 2026-05-17 | Simplificación del modelo (sin nombre, sin semilla manual) + 6ª estrategia BFS Ponderado + reporte PDF con gráficos JFreeChart + análisis cuantitativo del ganador + mejora visual GraphStream. Ver detalle abajo. |
| v6 | 2026-05-17 | Reversión del estilo visual de GraphStream a la versión previa (la nueva versión introducía bugs con la leyenda flotante y el título dinámico). Nuevo flujo del comparativo con `JTabbedPane` de 6 pestañas (una por estrategia). Modo individual ya no ofrece exportar PDF. Ver detalle abajo. |
| v7 | 2026-05-22 | Interfaz gráfica Swing completa con FlatDarkLaf — 4 ventanas nuevas que reemplazan al menú por consola como modo por defecto. Refinamiento visual integral del `GeneradorReportePDF` (paleta unificada, hero banner, tabla zebra con ganador destacado, score apilado). La consola sigue disponible vía `--consola` o headless. Ver detalle abajo. |
| v8 | 2026-05-23 | Comparación justa (paciente cero al 15% fijado antes de vacunar, idéntico para las 6 estrategias), barra de turno con conteo SIRV bajo el grafo y nuevo modo por lotes (N grafos distintos por estrategia, comparación promedio + acumulada). Ver detalle abajo. |
| v9 | 2026-05-23 | Glosario de métricas en los PDFs: ambos generadores (`GeneradorReportePDF` y `GeneradorReporteLotePDF`) agregan una página final "Glosario de métricas — Guía de interpretación" que explica cada variable (S, I, R, V, pico, t-pico, duración, afectados, contención %, R0, score compuesto, victorias), qué mide y qué valores son favorables. Ver detalle abajo. |

---

## Estructura de paquetes actual

```
src/main/java/
├── Context/                       (documentación viva del proyecto, sin código)
├── domain/
│   ├── model/        → package domain.model
│   ├── value/        → package domain.value
│   └── algoritmo/    → package domain.algoritmo
├── application/
│   ├── service/      → package application.service
│   ├── dto/          → package application.dto
│   └── command/      → package application.command
├── presentation/     → package presentation
├── infrastructure/
│   ├── persistence/  → package infrastructure.persistence
│   └── util/         → package infrastructure.util
└── _smoketest/       → package _smoketest          (temporal, eliminable)
```

---

## Cambios en pom.xml

- `groupId` → `epidemia`
- `artifactId` → `EpidemiaSimulador`
- `maven.compiler.release` → `17` (Java 17 LTS)
- `exec.mainClass` → `presentation.Main`
- Dependencias de GraphStream 2.0: `gs-core` + `gs-ui-swing`
- Plugin `exec-maven-plugin` para `mvn exec:java`

---

## Archivos con implementación completa

### `domain/value/` — Enums del dominio
| Archivo | Descripción |
|---|---|
| `EstadoSIRV.java` | 4 constantes: SUSCEPTIBLE, INFECTADO, RECUPERADO, VACUNADO. Javadoc con transiciones válidas. |
| `TipoEvento.java` | 3 constantes: ALERTA_LEVE (×0.80), CUARENTENA (×0.50), LOCKDOWN (×0.20). |
| `EstrategiaVacunacion.java` | 4 constantes con complejidad de cada algoritmo documentada. |

### `domain/model/` — Modelos del dominio
| Archivo | Descripción |
|---|---|
| `Persona.java` | Nodo del grafo. Ver sección "Cálculo de pesos" abajo — se agregaron `factorEdad()`, `factorEstrato()`, `factorOcupacion()`. |
| `Contacto.java` | Arista dirigida. `probContagio` ∈ [0.05, 0.95]. `aplicarFactor(double)` para eventos. `clamp()` en constructor y setter. |
| `RedSocial.java` | Grafo `HashMap<Persona, List<Contacto>>`. Métodos: `agregarPersona`, `agregarContacto`, `getContactos`, `getTodasLasPersonas`, `getPersonasPorEstado`, `getGrado`, `getTodosLosContactos`, `setearPacienteCero`. |
| `EventoEpidemiologico.java` | Evento de salud pública. Bandera `yaDisparado` evita reactivaciones. |

### `infrastructure/util/GeneradorPoblacion.java`
Implementación completa de `calcularProbContagio()`. Ver sección "Cálculo de pesos" abajo.

### `infrastructure/persistence/RedMemoryRepository.java`
Repositorio en memoria: `guardar(RedSocial)`, `obtener()`, `limpiar()`.

---

## Cálculo de pesos de arista (v3)

### Decisión de diseño

Los factores de riesgo viven en `Persona` (cada nodo conoce su propio perfil de riesgo).
`GeneradorPoblacion` los combina para calcular el peso de cada arista.
Esto mantiene la responsabilidad en el lugar correcto: el nodo sabe cuánto riesgo representa.

### Fórmula completa

```
probContagio(u → v) = probBase × fEdad(u) × fEstrato(u) × fOcupacion(u)
                      → normalizado a [0.05, 0.95]
```

Solo intervienen atributos del **origen (u)** porque la arista modela el riesgo
que introduce ese nodo al establecer contacto (su exposición y vulnerabilidad propias).

### Factores implementados en `Persona.java`

#### `factorEdad()`
```
fEdad = 1.0 + (edad / 100.0)

edad =  0 → 1.00
edad = 30 → 1.30
edad = 65 → 1.65
edad = 90 → 1.90
```
Mayor edad = mayor vulnerabilidad biológica al actuar como fuente de contagio.

#### `factorEstrato()`
```
fEstrato = 1.0 + 0.1 × (6 - estrato)

estrato 1 → 1.50  (hacinamiento, transporte público masivo)
estrato 2 → 1.40
estrato 3 → 1.30
estrato 4 → 1.20
estrato 5 → 1.10
estrato 6 → 1.00  (menor exposición al contacto masivo)
```
Estrato bajo = condiciones de vida con mayor densidad de contacto.

#### `factorOcupacion()`
```
"salud"      → 1.40  (exposición directa a enfermos)
"informal"   → 1.30  (vendedor callejero, sin protección)
"estudiante" → 1.20  (clusters escolares densos)
"empleado"   → 1.00  (entorno laboral moderado) ← default
"jubilado"   → 0.80  (pocas salidas, menos contacto)
```

### Método `calcularProbContagio` en `GeneradorPoblacion.java`

```java
// Versión con probBase configurable
public static double calcularProbContagio(Persona origen, double probBase) {
    double peso = probBase
            * origen.factorEdad()
            * origen.factorEstrato()
            * origen.factorOcupacion();
    return Math.max(0.05, Math.min(0.95, peso));
}

// Versión con probBase = 0.20 por defecto
public static double calcularProbContagio(Persona origen) { ... }
```

### Ejemplo numérico

```
origen: edad=65, estrato=1, ocupacion="salud", probBase=0.20

fEdad      = 1.0 + (65/100.0) = 1.65
fEstrato   = 1.0 + 0.1*(6-1)  = 1.50
fOcupacion = 1.40
─────────────────────────────────────
peso = 0.20 × 1.65 × 1.50 × 1.40 = 0.693  → dentro de [0.05, 0.95] ✓

origen: edad=20, estrato=6, ocupacion="jubilado", probBase=0.20

fEdad      = 1.0 + (20/100.0) = 1.20
fEstrato   = 1.0 + 0.1*(6-6)  = 1.00
fOcupacion = 0.80
─────────────────────────────────────
peso = 0.20 × 1.20 × 1.00 × 0.80 = 0.192  → dentro de [0.05, 0.95] ✓
```

---

## Archivos esqueleto (solo firma + javadoc, sin implementar)

### `domain/algoritmo/`
| Archivo | Estado | Responsabilidad | Métodos clave |
|---|---|---|---|
| `ModeloSIRV.java` | TODO | Propagación turno a turno | `simularTurno(RedSocial)` |
| `GestorEventos.java` | TODO | Evalúa umbrales, dispara eventos | `evaluar(RedSocial, int)` |
| `BFSPonderado.java` | TODO | Camino de mayor probContagio | `caminoMayorContagio`, `probMaxima` |
| `VacunacionAleatoria.java` | TODO | O(N) | `vacunar(RedSocial)` |
| `VacunacionHubs.java` | TODO | O(N log N) por grado | `vacunar(RedSocial)` |
| `VacunacionBetweenness.java` | TODO | O(N×(N+M)) | `calcularBetweenness`, `vacunar` |
| `VacunacionComunidades.java` | **✓ Implementado** | O(N+M) — prioriza grupos por densidad interna | `vacunar`, `calcularDensidadesPorEstrato` |
| `VacunacionHibrida.java` | TODO | Score compuesto, α/β/γ/δ definidos | `calcularScore`, `vacunar` |

### `application/`
| Archivo | Responsabilidad |
|---|---|
| `service/SimulacionService.java` | Loop de turnos + registro historial |
| `service/VacunacionService.java` | Switch por estrategia → delega al algoritmo |
| `dto/ConfiguracionDto.java` | Datos de entrada del usuario |
| `dto/ResultadoSimulacionDto.java` | Métricas y historial de una simulación |
| `command/IniciarSimulacionCommand.java` | Flujo completo + comparativo 4 estrategias |
| `command/AplicarVacunacionCommand.java` | Paso previo a la simulación |

### `presentation/`
| Archivo | Responsabilidad |
|---|---|
| `Main.java` | Punto de entrada único |
| `ConsolaMenu.java` | Interacción por consola (Scanner) |
| `GraficoSimulacion.java` | Integración con GraphStream 2.0 |
| `PanelEstadisticas.java` | Panel comparativo final |

### `infrastructure/`
| Archivo | Responsabilidad |
|---|---|
| `persistence/CargadorRedCSV.java` | Leer red desde archivos CSV |
| `persistence/ExportadorResultados.java` | Guardar resultado en .txt/.csv |
| `util/GeneradorPoblacion.java` | `calcularProbContagio` ✓ — fases de generación TODO |
| `util/CalculadorEstadisticas.java` | Calcular métricas finales |

---

## Archivos de datos

```
data/
├── personas_red1.csv    (cabecera: id,nombre,edad,estrato,ocupacion)
├── contactos_red1.csv   (cabecera: origen,destino,probContagio)
├── personas_red2.csv    (cabecera: id,nombre,edad,estrato,ocupacion)
└── contactos_red2.csv   (cabecera: origen,destino,probContagio)
```

Rellenar antes de usar `CargadorRedCSV.cargar()`.
Red 1 ≈ 80 nodos (barrio estrato 1–2). Red 2 ≈ 300 nodos (comunidad mixta).

---

## Convenciones de código

- `equals`/`hashCode` de `Persona` por `id` → clave válida de `HashMap`.
- `probContagio` clampado a `[0.05, 0.95]` en `Contacto` (constructor y setter).
- `yaDisparado` en `EventoEpidemiologico` → cada evento ocurre exactamente una vez.
- `Collections.unmodifiableSet` en `RedSocial.getTodasLasPersonas()` → evita modificación accidental.
- Constantes `ALPHA/BETA/GAMMA/DELTA` en `VacunacionHibrida` son `private static final` → fácil ajuste experimental.
- `factorOcupacion()` usa `switch` expression de Java 17 con `default → 1.00` para ocupaciones no reconocidas.

---

## Estrategias de vacunación — resumen comparativo

| # | Estrategia | Nivel | Complejidad | Estado | Rol en el análisis |
|---|---|---|---|---|---|
| 1 | `ALEATORIA` | Individuo | O(N) | TODO | Línea base |
| 2 | `HUBS` | Individuo | O(N log N) | TODO | Efectiva en redes Scale-Free |
| 3 | `BETWEENNESS` | Individuo | O(N×(N+M)) | TODO | Efectiva bloqueando puentes inter-comunidad |
| 4 | `COMUNIDADES` | **Grupo** | O(N+M) | **✓** | Corta propagación intra-comunidad |
| 5 | `HIBRIDA` | Individuo | O(N×(N+M)) | TODO | **Aporte propio** — combina estructura + demografía |

**Nota:** 5 estrategias supera el mínimo del rubric (4). COMUNIDADES e HIBRIDA se presentan como par:
- COMUNIDADES = enfoque de grupo, interpretable, de la literatura clásica.
- HIBRIDA = enfoque individual refinado, contribución propia del equipo.

---

## Detalle: VacunacionComunidades (v4)

### Fundamento

En redes con estructura de comunidades, el contagio ocurre primero y más rápido
*dentro* del grupo. Vacunar en los grupos más densos interrumpe esos focos antes
de que la infección salte a otras comunidades.

### Definición de comunidad

Se aproxima **comunidad = estrato socioeconómico**. Justificación: `GeneradorPoblacion`
construye los clusters familiares y vecinales dentro del mismo estrato, así que el
estrato es un proxy válido del grupo social colombiano.

### Algoritmo implementado

```
1. Agrupar SUSCEPTIBLE por estrato         → Map<Integer, List<Persona>>

2. Calcular densidad interna de cada grupo:
     densidad(g) = aristasInternas(g) / (|g| × (|g| - 1))
   Las aristas internas son las que tienen origen Y destino dentro del mismo grupo.

3. Ordenar grupos de mayor a menor densidad.

4. Vacunar desde el grupo más denso:
     - Dentro del grupo, priorizar por grado (más conectado primero).
     - Parar cuando se alcanza el 20% de susceptibles totales.
```

### Métodos públicos

| Método | Descripción |
|---|---|
| `vacunar(RedSocial)` | Ejecuta el algoritmo completo. Retorna lista de vacunados. |
| `calcularDensidadesPorEstrato(RedSocial)` | Expone las densidades para logs y análisis. |

### Ejemplo de salida (log esperado)

```
Estrato 1 → densidad = 0.42  ← vacunar primero
Estrato 2 → densidad = 0.35
Estrato 3 → densidad = 0.18
Estrato 4 → densidad = 0.09
Estrato 5 → densidad = 0.05
Estrato 6 → densidad = 0.02  ← vacunar último (si queda cuota)
```

---

## Orden de implementación (por dependencias)

```
Día 1: ✓ EstadoSIRV · TipoEvento · EstrategiaVacunacion (+ COMUNIDADES)
       ✓ Persona · Contacto · RedSocial · EventoEpidemiologico
       ✓ Persona.factorEdad/factorEstrato/factorOcupacion
       ✓ GeneradorPoblacion.calcularProbContagio
       ✓ VacunacionComunidades (implementación completa)

Día 2: GeneradorPoblacion.generar() (5 fases) · ModeloSIRV

Día 3: VacunacionAleatoria · VacunacionHubs
       VacunacionBetweenness · VacunacionHibrida · GestorEventos

Día 4: VacunacionService · SimulacionService
       ConfiguracionDto · IniciarSimulacionCommand · ConsolaMenu

Día 5: GraficoSimulacion (GraphStream) · PanelEstadisticas

Día 6: CargadorRedCSV · rellenar CSVs · CalculadorEstadisticas
       ExportadorResultados · pruebas de integración

Día 7: BFSPonderado · ajuste fino · análisis comparativo 5 estrategias · informe
```

---

## v5 — Refactorización integral del análisis y la UX (2026-05-17)

### 1. Eliminación de campos al usuario

#### Semilla aleatoria
Antes el usuario tenía que ingresar una `semilla` en cada simulación (default 42).
Ahora se genera automáticamente con `ThreadLocalRandom.current().nextLong()` en el
constructor de `ConfiguracionDto`, sin exposición al usuario.

- **¿Por qué se mantiene como campo?** Reproducibilidad **interna** dentro del
  comparativo: las 6 estrategias deben correr sobre exactamente la misma red, los
  mismos pacientes cero y el mismo RNG de propagación. Sin un valor compartido,
  cada estrategia operaría sobre redes distintas y la comparación sería inválida.
- **Lo que cambió:**
  - `ConsolaMenu.solicitarConfiguracion()` ya no pregunta la semilla.
  - `IniciarSimulacionCommand` ya no la imprime en el log de "Generando red".
  - `ConfiguracionDto.toString()` la omite del resumen mostrado al usuario.
  - Se eliminó el helper `leerLongDefault` del `ConsolaMenu`.

#### Nombre y apellido en `Persona`
Se eliminaron por completo del modelo. Los nodos se identifican únicamente por su
`id` (P001, P002, ...). Cambios:

- `Persona`: se quitó el campo `nombre`, el constructor pasó de
  `Persona(id, nombre, edad, estrato, ocupacion)` a `Persona(id, edad, estrato, ocupacion)`.
  Se eliminaron `getNombre()` y `setNombre()`. `toString()` ya no lo expone.
- `GeneradorPoblacion`: se eliminaron los arrays `NOMBRES[]` y `APELLIDOS[]` y la
  lógica de generación con sufijos numéricos para garantizar unicidad. Ahora hay un
  método `generarPersona(id, rand)` mucho más corto.
- `CargadorRedCSV`: acepta dos formatos para compatibilidad:
  - **Nuevo (4 columnas):** `id,edad,estrato,ocupacion`
  - **Antiguo (5 columnas):** `id,nombre,edad,estrato,ocupacion` — la columna
    nombre se ignora automáticamente. Los CSV antiguos siguen funcionando.

### 2. Sexta estrategia — `VacunacionBFSPonderado`

Nueva clase `domain/algoritmo/VacunacionBFSPonderado.java`. Reutiliza el algoritmo
`BFSPonderado` (antes solo usado para análisis post-simulación) como criterio de
vacunación.

**Algoritmo (alto nivel):**
1. Selecciona K = 5 candidatos a paciente cero (los nodos de mayor grado entre los
   susceptibles, porque son los más expuestos a ser focos iniciales).
2. Desde cada candidato, ejecuta `BFSPonderado.caminoMayorContagio` hacia todos los
   demás susceptibles, obteniendo el camino de mayor probabilidad acumulada.
3. Para cada nodo intermedio del camino (excluye foco y destino), acumula como
   score la probabilidad acumulada del camino completo. Los nodos que están en
   muchas rutas críticas reciben score alto.
4. Vacuna el 20% con mayor score.

**Complejidad:** O(K × N × (N+M) log N). Con K pequeño (5), es notablemente más
barato que Betweenness puro pero captura señal similar de importancia estructural.

**Lugar en el ecosistema de estrategias:** complementa a `Betweenness` (que mide
caminos más cortos) usando en cambio caminos de **máxima probabilidad de contagio**
— una métrica más alineada al riesgo epidemiológico real.

**Integración:**
- `EstrategiaVacunacion`: nuevo valor `BFS_PONDERADO`.
- `VacunacionService`: nueva rama en el switch.
- `ConsolaMenu`: opción 6 en el menú de estrategia.
- `IniciarSimulacionCommand.ejecutarComparativo`: itera todos los valores del enum,
  por lo que la nueva estrategia entra automáticamente en el comparativo.

### 3. Análisis cuantitativo del ganador — `AnalisisComparativo`

Nueva clase `infrastructure/util/AnalisisComparativo.java` que determina la
estrategia óptima con un **score compuesto** sobre las 5 métricas del modelo
matemático (sección 7 de `03_modelo_matematico.md`).

**Fórmula:**
```
score = 0.30 · norm_inv(picoMaximoInfectados)
      + 0.15 · norm_inv(duracionBrote)
      + 0.25 · norm_inv(totalRecuperados)
      + 0.20 · norm(porcentajeContencion)
      + 0.10 · norm_inv(R0Estimado)

∑ pesos = 1.0   →   score ∈ [0, 1]
```

`norm_inv` invierte las métricas "menor = mejor" (pico, duración, afectados, R0)
para que en todos los casos un valor más alto del término signifique "mejor".

**Salida:** `List<ScoreEstrategia>` ordenada de mayor a menor score. El primer
elemento es la estrategia ganadora con su desglose por componente y una
`justificarGanador()` que produce el texto narrativo del veredicto.

**Por qué estos pesos:**
- `0.30 pico`: presión sobre el sistema de salud — la métrica de impacto más visible.
- `0.25 afectados`: cuántos pasaron por la enfermedad — define el costo humano total.
- `0.20 contención`: cuánta población nunca se infectó (incluye los vacunados).
- `0.15 duración`: brote corto = menos disrupción social.
- `0.10 R0`: velocidad inicial — informativa, pero correlacionada con pico.

### 4. Reporte PDF profesional — `GeneradorReportePDF`

Nueva clase `infrastructure/persistence/GeneradorReportePDF.java` que produce un
PDF de 7 páginas con gráficos vectoriales generados con JFreeChart y embebidos vía
OpenPDF (fork libre de iText 4.x).

**Estructura del PDF:**
1. **Portada** — título, fecha, tamaño de red, número de estrategias.
2. **Tabla resumen** — pico, t-pico, duración, afectados, contención%, R0 por estrategia.
3. **Veredicto cuantitativo** — estrategia ganadora con score y justificación.
4. **Curva I(t) comparativa** — todas las estrategias superpuestas en un solo gráfico.
5. **Curvas SIRV por estrategia** — un mini-gráfico por estrategia con las 4 series.
6. **Barras por métrica** — un gráfico de barras por cada métrica (pico, duración,
   afectados, contención, R0).
7. **Desglose del score** — gráfico de barras apiladas que muestra qué componente
   contribuyó más al score de cada estrategia.

**Dependencias añadidas en `pom.xml`:**
```xml
<dependency>
    <groupId>org.jfree</groupId>
    <artifactId>jfreechart</artifactId>
    <version>1.5.4</version>
</dependency>
<dependency>
    <groupId>com.github.librepdf</groupId>
    <artifactId>openpdf</artifactId>
    <version>1.3.34</version>
</dependency>
```

**Integración en menú:** el flujo `ofrecerExportacion()` de `ConsolaMenu` ofrece
ahora tres opciones: TXT, PDF, o ambos.

### 5. Mejora visual del `GraficoSimulacion` (GraphStream)

Cambios en `presentation/GraficoSimulacion.java`:

- **CSS más rico:** sombras, bordes blancos, padding del fondo, texto en negrita y
  alineación a la derecha del nodo para que no se solape con la figura.
- **Layout más expansivo:** `layout.force = 3.0` y `layout.gravity = 0.05` separan
  más los nodos. Esto resuelve la queja inicial de "no veo 80 nodos" — ahora se
  separan claramente.
- **Tamaño dinámico ajustado:** `10 + min(30, grado × 1.5)`. Antes los hubs muy
  grandes tapaban nodos vecinos.
- **Leyenda flotante en el grafo:** 4 nodos especiales con clase `legenda` colocados
  en una esquina, cada uno con el color de su estado y la etiqueta del estado.
  Así el usuario ve el mapa de colores sin salir de la ventana.
- **Título dinámico:** el título de la ventana muestra en vivo
  `"Epidemia SIRV — Turno N | S:## I:## R:## V:##"` para ver el conteo SIRV sin
  mirar la consola.

### 6. Comparativo: de 5 a 6 estrategias

- `ConsolaMenu`: el banner del menú principal cambió de
  "Comparativo 5 estrategias" a "Comparativo 6 estrategias".
- `PanelEstadisticas.graficoAscii`: se añadió un sexto símbolo (`◇`) a la paleta
  para distinguir la curva de `BFS_PONDERADO`.

### 7. Resumen de archivos modificados / creados

#### Modificados
- `pom.xml`
- `src/main/java/Context/04_estructura_creada.md`
- `src/main/java/application/dto/ConfiguracionDto.java`
- `src/main/java/application/command/IniciarSimulacionCommand.java`
- `src/main/java/application/service/VacunacionService.java`
- `src/main/java/domain/model/Persona.java`
- `src/main/java/domain/value/EstrategiaVacunacion.java`
- `src/main/java/infrastructure/persistence/CargadorRedCSV.java`
- `src/main/java/infrastructure/util/GeneradorPoblacion.java`
- `src/main/java/presentation/ConsolaMenu.java`
- `src/main/java/presentation/GraficoSimulacion.java`
- `src/main/java/presentation/PanelEstadisticas.java`

#### Creados
- `src/main/java/domain/algoritmo/VacunacionBFSPonderado.java`
- `src/main/java/infrastructure/persistence/GeneradorReportePDF.java`
- `src/main/java/infrastructure/util/AnalisisComparativo.java`

### 8. Pruebas realizadas
- `mvn compile`: sin errores.
- Ejecución del comparativo (red 80 nodos, 20 turnos, 6 estrategias, headless):
  todas las estrategias completan, el score compuesto rankea, el reporte PDF de
  7 páginas se genera correctamente (~188 KB).

---

## v6 — Reversión visual y comparativo con tabs (2026-05-17)

### 1. Reversión de `GraficoSimulacion`

La nueva versión de v5 (con leyenda flotante mediante "nodos legenda", título
dinámico con conteo SIRV y CSS expandido) introducía bugs al renderizar los nodos
de leyenda dentro del propio layout de fuerzas. Se revirtió a la versión estable
anterior:

- CSS original (sombra suave, etiqueta debajo del nodo con fondo redondeado).
- Layout simple `layout.force = 2.2`, `layout.quality = 4`.
- Sin leyenda flotante: el código de los nodos especiales `__leg_N` se eliminó.
- Sin título dinámico con conteo: se restauró el título estático
  `"Epidemia SIRV — Turno N"`.

### 2. Nuevo modo embebido en `GraficoSimulacion`

Se conservó la API pública anterior y se añadió un segundo modo de inicialización
para que la misma clase pueda usarse standalone (en su propia ventana) o
embebida (dentro de un contenedor Swing):

```java
public void inicializar(RedSocial red);              // standalone (graph.display())
public JComponent inicializarEmbebido(RedSocial red); // retorna ViewPanel para JTabbedPane
```

Internamente comparte el método privado `construirGrafo(red)` que arma nodos y
aristas. La diferencia es que `inicializarEmbebido` instancia un `SwingViewer` y
extrae su `ViewPanel` sin abrir un `JFrame` propio.

### 3. Nueva clase `presentation/VentanaComparativaTabs.java`

Ventana única con un `JTabbedPane` que va recibiendo las 6 vistas embebidas del
comparativo, una por estrategia. La pestaña recién agregada se selecciona
automáticamente para que el usuario vea la simulación que está corriendo en ese
momento. Al final del comparativo las 6 pestañas quedan disponibles para
inspección visual.

Modo a prueba de fallos: si Swing/GraphStream no logran inicializarse (por ej.
en headless), `isDisponible() == false` y el comparativo cae con gracia al modo
sin visualización.

### 4. Nuevo flujo en `IniciarSimulacionCommand.ejecutarComparativo`

```
si visualización está activada:
    crear VentanaComparativaTabs
    para cada estrategia:
        construir red (misma semilla → misma estructura)
        crear GraficoSimulacion en modo embebido
        agregar su ViewPanel como nueva pestaña
        aplicar vacunación + paciente cero
        ejecutar SimulacionService con animación en esa pestaña
si no:
    flujo headless tradicional
```

Cada estrategia corre **secuencialmente** sobre una red recién generada (con la
misma semilla, por lo que la estructura del grafo es idéntica). Lo que cambia
entre pestañas es qué nodos están vacunados y cómo evolucionó la epidemia.

### 5. Cambios en `ConsolaMenu`

- `ejecutarIndividual` ya no llama a la exportación con PDF; usa el nuevo helper
  `ofrecerExportacionTexto()` que solo ofrece TXT. Razón: con una sola estrategia
  el PDF comparativo carece de sentido.
- `ejecutarComparativo` ya **no** fuerza `mostrarVisualizacion = false`; respeta
  la elección del usuario y delega al `IniciarSimulacionCommand` que decide si
  crear la ventana de tabs o ejecutar headless.
- Helper `ofrecerExportacionCompleta()` (TXT / PDF / ambos) sigue solo en
  comparativo.

### 6. Archivos creados/modificados en v6

#### Modificados
- `src/main/java/Context/04_estructura_creada.md`
- `src/main/java/application/command/IniciarSimulacionCommand.java`
- `src/main/java/presentation/ConsolaMenu.java`
- `src/main/java/presentation/GraficoSimulacion.java`

#### Creados
- `src/main/java/presentation/VentanaComparativaTabs.java`

### 7. Pruebas realizadas
- `mvn compile`: sin errores.
- Comparativo headless (sin visualización): las 6 estrategias corren, el score
  compuesto rankea, el reporte PDF se sigue exportando si se solicita.
- La ventana de tabs solo se crea cuando el usuario activa visualización; en
  headless no se intenta abrir Swing.

---

## v7 — Interfaz Swing + refinamiento visual del PDF (2026-05-22)

Hasta v6 el flujo de uso era 100% por consola: el usuario corría
`mvn exec:java`, navegaba por menús de texto, y la única ventana gráfica era el
grafo de GraphStream. En v7 esa fricción desaparece: el programa abre una
**interfaz Swing oscura y moderna** (FlatDarkLaf) con tres ventanas — menú
principal, configuración y resultados — y la consola queda como fallback para
entornos sin GUI o usuarios que prefieren CLI. En paralelo, el reporte PDF
recibió una refacción visual completa: paleta consistente, hero banner,
tabla zebra con ganador destacado, leyenda de colores en barras, y desglose
del score apilado.

### 1. Nueva dependencia — FlatLaf 3.5.4

```xml
<dependency>
    <groupId>com.formdev</groupId>
    <artifactId>flatlaf</artifactId>
    <version>3.5.4</version>
</dependency>
```

Se eligió FlatDarkLaf porque:
- API simple — un único `FlatDarkLaf.setup()` antes de instanciar Swing.
- Estética alineada con NetBeans/IntelliJ — familiar para el evaluador.
- Permite personalizar `arc` (esquinas redondeadas) por componente vía
  `UIManager.put`.

`Main.java` activa FlatDarkLaf y configura los `arc` antes de crear ningún
componente:

```java
com.formdev.flatlaf.FlatDarkLaf.setup();
UIManager.put("Button.arc", 12);
UIManager.put("Component.arc", 12);
UIManager.put("ProgressBar.arc", 12);
UIManager.put("TextComponent.arc", 8);
UIManager.put("ScrollBar.thumbArc", 999);
```

### 2. Nuevo flujo de arranque en `Main.java`

```
java -jar EpidemiaSimulador.jar               → abre VentanaMenuPrincipal (Swing)
java -jar EpidemiaSimulador.jar --consola     → fuerza ConsolaMenu (texto)
java -jar ... (entorno headless)              → cae automáticamente a ConsolaMenu
```

La detección headless es nativa (`GraphicsEnvironment.isHeadless()`), por lo que
servidores y CI siguen funcionando sin cambios.

### 3. Cuatro ventanas Swing nuevas en `presentation/`

#### `VentanaMenuPrincipal.java`
- Ventana raíz. Hereda de `JFrame`, `EXIT_ON_CLOSE`.
- **Banner azul** con título, universidad y curso.
- **JRadioButton** para elegir modo (individual / comparativo).
- Botones **Continuar** (default, énfasis FlatLaf) y **Salir**.
- Al pulsar Continuar abre `VentanaConfiguracion` modal; si el usuario
  confirma, lanza la simulación en un `SwingWorker` para no bloquear la UI.

#### `VentanaConfiguracion.java` (modal)
- Reemplaza al método `solicitarConfiguracion()` de `ConsolaMenu`.
- Formulario con `GridBagLayout`:
  - `JComboBox` para el tamaño de red (pequeña / grande / personalizado).
  - `JTextField` para N personas (solo si "personalizado").
  - `JComboBox<EstrategiaVacunacion>` (solo en modo individual; en
    comparativo se sustituye por un label explicativo).
  - `JSpinner` para turnos máximos y días de recuperación.
  - `JCheckBox` "Mostrar visualización GraphStream" (default activo).
  - `JCheckBox` "Cargar red desde archivos CSV".
- Botones Cancelar / **Ejecutar ▶** (default).
- Construye un `ConfiguracionDto` y lo expone vía `getConfiguracion()`
  (devuelve `null` si el usuario canceló).

#### `VentanaResultados.java`
- Ventana de salida. Reemplaza al panel ASCII de `PanelEstadisticas` y a la
  oferta de exportación de `ConsolaMenu`.
- **JTabbedPane** con dos pestañas:
  - **"Tabla y ranking"** — `JSplitPane` vertical con un `JTable`
    (métricas por estrategia, fila ganadora resaltada con `RendererGanador`) y
    un `JTextArea` monoespaciado (ranking con score compuesto + justificación).
  - **"Curva I(t)"** — `ChartPanel` de JFreeChart con la curva de infectados
    por estrategia, repintado en paleta dark para integrarse con FlatLaf.
- Botones inferiores: **Exportar TXT**, **Exportar PDF** (solo en comparativo),
  **Cerrar**. Usan `JFileChooser` con extensión sugerida y filtro de archivo.

#### `DialogoProgreso.java`
- `JDialog` modal con barra `JProgressBar` indeterminada.
- Se muestra mientras el `SwingWorker` ejecuta la simulación en background, de
  modo que la ventana principal no aparezca congelada.

### 4. Refacción visual integral de `GeneradorReportePDF`

El PDF recibió una pasada de diseño completa. Sigue siendo light theme
(porque un PDF debe leerse en papel/pantalla clara), pero ahora con paleta
unificada y elementos visuales consistentes.

**Paleta nueva** (constantes `private static final` en la clase):

| Constante | Hex | Uso |
|---|---|---|
| `COLOR_ACENTO` | `#2c5fa6` | Azul corporativo: banner hero, cabecera tabla |
| `COLOR_TITULO` | `#111827` | Texto de títulos H1/H2 |
| `COLOR_BODY` | `#374151` | Texto de cuerpo y celdas |
| `COLOR_SUAVE` | `#6b7280` | Texto secundario (labels, etiquetas de eje) |
| `COLOR_GRID` | `#e5e7eb` | Líneas de cuadrícula en gráficos |
| `COLOR_FONDO` | `#fafbfc` | Fondo del plot de cada gráfico |
| `COLOR_AXIS` | `#d1d5db` | Líneas de ejes |
| `COLOR_GANA_BG` | `#dcfce7` | Fondo verde claro de la fila ganadora |
| `COLOR_GANA_TXT` | `#166534` | Texto verde oscuro de la fila ganadora |
| `COLOR_ZEBRA` | `#f6f7f9` | Fondo alternativo (filas impares) |
| `PALETA[6]` | varios | Color por estrategia, también en la UI Swing futura |

**Mejoras visuales clave:**

1. **Hero banner en la portada** — bloque azul con título blanco y subtítulo
   universidad, en lugar del título plano anterior. Bajo el hero, una tabla
   2-col (`Generado` / `Tamaño de red` / `Estrategias evaluadas`) con
   tipografía en mayúsculas y color suave para las etiquetas.
2. **Tabla resumen** — primera columna es un pequeño **cuadrito de color por
   estrategia** (mismo color que la PALETA, reutilizable en barras y curvas).
   Filas alternadas con `COLOR_ZEBRA`, fila del ganador con fondo verde claro
   y prefijo `*`. Cabecera con fondo azul corporativo y texto blanco.
3. **Veredicto cuantitativo** — el ganador aparece como un párrafo verde
   destacado con el score `X.YYY / 1.000`, seguido de la justificación
   narrativa generada por `AnalisisComparativo.justificarGanador`.
4. **Curva I(t) comparativa** — XY chart con renderer que aplica `PALETA[i]`
   por serie. Trazos de 2.2pt, sin marcadores. Cuadrícula sutil en
   `COLOR_GRID`, ejes en `COLOR_AXIS`. Leyenda inferior sin borde.
5. **Curvas SIRV por estrategia** — un mini-chart por estrategia (520×210)
   con paleta fija por estado (S azul, I rojo, R verde, V naranja) en lugar
   de los colores aleatorios anteriores.
6. **Barras de métricas** — 5 charts (pico, duración, afectados, contención,
   R0), cada uno con `BarRenderer` que pinta cada barra con el color de su
   estrategia (mismo `PALETA[i]`). Encima de la serie de barras se imprime
   una leyenda horizontal con un cuadrito de color por estrategia, para que
   el lector identifique las barras sin tener que mirar el eje X.
7. **Etiquetas sobre las barras** — `StandardCategoryItemLabelGenerator` con
   `ItemLabelAnchor.OUTSIDE12` muestra el valor numérico encima de cada barra.
8. **Desglose del score** — stacked bar chart 520×340 donde cada estrategia
   aparece como una barra apilada y cada componente del score (`Pico`,
   `Duración`, `Afectados`, `Contención`, `R0`) ocupa un tramo de color
   distinto. La leyenda explica la fórmula y los pesos antes del gráfico.

**Mantras de estilo aplicados a todos los charts:**
- `chart.setBorderVisible(false)` + `setPadding(8,4,4,4)`.
- `plot.setBackgroundPaint(COLOR_FONDO)`, `plot.setOutlineVisible(false)`.
- Sin sombras de barras (`shadowVisible=false`, `barPainter=Standard`).
- `setMaximumBarWidth(0.13)` en simples, `0.10` en apiladas — evita barras
  gigantes con pocas categorías.
- Categorías rotadas 45° (`CategoryLabelPositions.DOWN_45`) para que los
  nombres largos como `BFS_PONDERADO` no se solapen.

### 5. Cambios menores en el flujo

- `IniciarSimulacionCommand` se invoca igual desde Swing que desde consola; no
  hubo que cambiarlo. `VentanaMenuPrincipal` lo envuelve en un `SwingWorker`.
- `ConsolaMenu` sigue intacto — el comparativo desde consola sigue ofreciendo
  exportación a TXT/PDF. Es el path de fallback cuando Swing no está disponible.
- `Main.java` reemplaza la antigua llamada directa `new ConsolaMenu().iniciar()`
  por una rama que decide entre Swing y consola.

### 6. Smoke test temporal — `_smoketest/PdfSmoke.java`

Se añadió un main rápido fuera del flujo de producción para validar que
`GeneradorReportePDF` no rompa en runtime tras los cambios visuales. Genera
6 series sintéticas con curvas gaussianas y exporta `smoke_reporte.pdf`.

> **TODO de limpieza:** eliminar el paquete `_smoketest/` antes de la entrega
> final. El javadoc del archivo ya lo señala.

### 7. Archivos creados / modificados en v7

#### Modificados
- `pom.xml` (nueva dependencia FlatLaf 3.5.4)
- `src/main/java/Context/02_planificacion_tecnica.md` (estructura completa
  actualizada con los archivos Swing y dependencias)
- `src/main/java/Context/04_estructura_creada.md` (este archivo)
- `src/main/java/presentation/Main.java` (arranque Swing por defecto)
- `src/main/java/infrastructure/persistence/GeneradorReportePDF.java`
  (refacción visual integral — ~340 líneas modificadas)

#### Creados
- `src/main/java/presentation/VentanaMenuPrincipal.java`
- `src/main/java/presentation/VentanaConfiguracion.java`
- `src/main/java/presentation/VentanaResultados.java`
- `src/main/java/presentation/DialogoProgreso.java`
- `src/main/java/_smoketest/PdfSmoke.java` (temporal, eliminable)

### 8. Pruebas realizadas
- `mvn compile`: sin errores con la nueva dependencia FlatLaf.
- Lanzamiento gráfico: el menú principal abre con FlatDarkLaf, el flujo
  individual y comparativo encadena correctamente las tres ventanas
  (Menú → Configuración → Resultados).
- Modo headless (`java.awt.headless=true`): cae a `ConsolaMenu` sin errores.
- Flag `--consola`: fuerza el menú de texto incluso con GUI disponible.
- Smoke test (`PdfSmoke`): genera un PDF de ~180 KB con las 6 series
  sintéticas; todas las páginas y gráficos se renderizan.

### 9. Revisión del PDF — estado y observaciones

Tras la refacción, el PDF cumple los criterios de un reporte profesional:
portada, tabla comparativa, veredicto, gráficos vectoriales y desglose. La
revisión visual no encontró bugs de renderizado. Observaciones menores que
**no bloquean** pero podrían pulirse:

- La leyenda horizontal de barras usa `2N` columnas (cuadro + texto por
  estrategia). Con N=6, cada par tiene ~87px de ancho en el bloque de 520px,
  suficiente para los nombres actuales pero ajustado si en el futuro se
  añaden estrategias con nombres más largos.
- El asterisco `*` se usa en lugar de `★` Unicode para marcar al ganador,
  porque la fuente Helvetica embebida en OpenPDF no garantiza todos los
  glifos de iconos. Decisión intencional, no es un bug.
- `_smoketest/PdfSmoke.java` debe eliminarse antes de la entrega final.

---

## v8 — Comparación justa, barra de turno y modo por lotes (2026-05-23)

Tres mejoras pedidas por el equipo: que la comparación entre estrategias parta
de condiciones idénticas, que el grafo muestre el turno en curso, y un tercer
modo de análisis estadístico sobre muchos grafos.

### 1. Comparación justa — paciente cero antes de vacunar + 15% de infectados

**Problema previo.** En `IniciarSimulacionCommand` se vacunaba PRIMERO y luego se
elegía el paciente cero sobre los susceptibles restantes. Como cada estrategia
vacuna nodos distintos, el conjunto de susceptibles cambiaba y, aun con la misma
semilla, los infectados iniciales terminaban siendo distintos por estrategia → la
comparación no era justa.

**Cambio 1a — orden.** Ahora `setearPacienteCero(...)` se ejecuta **antes** de
`vacunacionCommand.ejecutar(...)` en los métodos `ejecutar` y `ejecutarConTab`. El
paciente cero se elige sobre la población completa e idéntica (misma red + misma
semilla) → las 6 estrategias arrancan con exactamente los mismos infectados. Como
todos los algoritmos de vacunación filtran por `SUSCEPTIBLE`, los infectados quedan
excluidos automáticamente (nunca se vacuna a un nodo ya infectado).

**Cambio 1b — 15% de infectados iniciales.** `ConfiguracionDto.setTamanoRed(N)`
recalcula `cantidadPacientesCero = round(0.15 × N)` (constante
`FRACCION_INFECTADOS_INICIALES = 0.15`). Al centralizarlo en el setter, tanto la UI
Swing como la consola y el comparativo aplican la regla sin duplicarla. Con N=80 →
12 infectados; N=300 → 45. La cuota de vacunación (20%) se calcula sobre los
susceptibles **restantes** tras fijar el paciente cero.

- Archivos: `application/command/IniciarSimulacionCommand.java`,
  `application/dto/ConfiguracionDto.java`.

### 2. Barra de turno con conteo SIRV bajo el grafo

`GraficoSimulacion` ahora envuelve la vista de GraphStream en un `JPanel`
(`BorderLayout`) con un `JLabel` al sur que muestra
`Turno N      S: ..  I: ..  R: ..  V: ..`, actualizado en `actualizarTurno(...)`
vía `SwingUtilities.invokeLater` (la simulación corre en un hilo de fondo).

- `inicializarEmbebido(red)` devuelve ese wrapper → el contador aparece en cada
  pestaña del comparativo.
- `inicializar(red)` (modo individual) construye su propio `JFrame` con el mismo
  wrapper en lugar de `graph.display()`, para mostrar también la barra de turno.
- Archivo: `presentation/GraficoSimulacion.java`.

### 3. Tercer modo — experimento por lotes (N grafos distintos por estrategia)

Nuevo modo que evalúa cada estrategia sobre **N grafos distintos e
independientes** y promedia los resultados. No hay animación (es cómputo puro).

**Semillas.** Cada par (estrategia `s`, corrida `i`) usa una semilla única
`base + s·N + i`, por lo que un `x100` genera 600 grafos diferentes; ninguno se
comparte entre estrategias ni entre corridas.

**Orquestación.** `IniciarSimulacionCommand.ejecutarLote(config, nGrafos, progreso)`
recorre las 6 estrategias × N grafos, creando un command por corrida (para ligar
generación, vacunación aleatoria y paciente cero a la semilla del grafo) y
reportando avance vía la interfaz funcional `ProgresoLote`.

**Agregación.** `infrastructure/util/AgregadorLote`:
- Promedia las métricas de las N corridas de cada estrategia (pico, turno-pico,
  duración, recuperados, vacunados, R0) y construye una curva I(t) promedio.
- Cuenta **victorias**: por cada índice de corrida rankea las estrategias con
  `AnalisisComparativo` y suma una victoria a la mejor (lectura "acumulada",
  estimación Monte Carlo de qué tan seguido cada estrategia es la mejor).
- Empaqueta todo en `application/dto/ResultadoLoteDto`.

**Veredicto (promedio + acumulado).** Sobre los 6 promedios se aplica el mismo
score compuesto del comparativo; el ranking por score promedio es el titular y la
columna de victorias corrobora el resultado.

**UI.**
- `presentation/ModoSimulacion` (enum): `INDIVIDUAL`, `COMPARATIVO`, `LOTE`.
- `VentanaMenuPrincipal`: tercer `JRadioButton` + flujo `ejecutarLote` en un
  `SwingWorker` que devuelve `ResultadoLoteDto`.
- `VentanaConfiguracion`: ahora recibe `ModoSimulacion`; muestra un `JSpinner`
  "Grafos por estrategia" (default 10) solo en modo lote; oculta visualización y
  CSV en lote (la carga CSV daría siempre la misma red).
- `VentanaResultadosLote`: tabla de métricas promedio + victorias, ranking por
  score compuesto promedio, curva I(t) promedio y export TXT/PDF (reutiliza
  `ExportadorResultados` y `GeneradorReportePDF` sobre los promedios).
- `DialogoProgreso`: nuevo `actualizar(completadas, total, detalle)` que pasa la
  barra a modo determinado ("grafo X de N").

### 4. Archivos creados / modificados en v8

#### Creados
- `src/main/java/presentation/ModoSimulacion.java`
- `src/main/java/presentation/VentanaResultadosLote.java`
- `src/main/java/application/dto/ResultadoLoteDto.java`
- `src/main/java/infrastructure/util/AgregadorLote.java`

#### Modificados
- `src/main/java/application/command/IniciarSimulacionCommand.java` (orden paciente
  cero / vacunación + `ejecutarLote` + `ProgresoLote`)
- `src/main/java/application/dto/ConfiguracionDto.java` (paciente cero = 15%)
- `src/main/java/presentation/GraficoSimulacion.java` (barra de turno)
- `src/main/java/presentation/VentanaConfiguracion.java` (modo + spinner de grafos)
- `src/main/java/presentation/VentanaMenuPrincipal.java` (tercer modo + flujo lote)
- `src/main/java/presentation/DialogoProgreso.java` (progreso determinado)
- `README.md`, `src/main/java/Context/01_contexto_proyecto.md`,
  `src/main/java/Context/05_grafos_y_algoritmos.md`

### 5. Pruebas realizadas
- `mvn clean compile`: BUILD SUCCESS sin errores.
- Smoke test headless (eliminado tras validar): paciente cero idéntico con la
  misma semilla (12 nodos para N=80); lote de 3 grafos × 6 estrategias = 18
  simulaciones, suma de victorias = 3 (una por grafo), vacunados ≈ 13 (20% de los
  68 susceptibles tras fijar el 15% infectado).

---

## v9 — Glosario de métricas en los PDFs (2026-05-23)

Los informes PDF carecían de una guía que explicara qué significa cada variable y
qué valores se consideran buenos. Sin esa referencia, un lector sin formación
epidemiológica podía interpretar mal, por ejemplo, que un R0 alto es deseable.

### 1. Nueva página de glosario en ambos generadores

Se añadió el método privado `agregarGlosario(Document doc)` a
`GeneradorReportePDF` y `agregarGlosario(Document doc, boolean esLote)` a
`GeneradorReporteLotePDF`. Ambos son la última sección del PDF, tras el desglose
del score.

#### Contenido de la página

Tabla de 3 columnas: **Variable | Qué mide | Resultado favorable**

| Variable | Qué mide | Resultado favorable |
|---|---|---|
| S — Susceptibles | Personas que pueden contagiarse | Alto al final del brote |
| I — Infectados | Personas enfermas y contagiosas | Curva baja y estrecha |
| R — Recuperados (afectados) | Personas que pasaron por la enfermedad | Número bajo |
| V — Vacunados | Personas inmunizadas antes de infectarse | Número alto |
| Pico máximo de infectados | Presión sobre el sistema de salud | **Menor = mejor** |
| t-Pico (turno del pico) | Momento en que explotó el brote | **Mayor = mejor** |
| Duración del brote | Turnos hasta que I = 0 | **Menor = mejor** |
| Total de afectados | Cuántos enfermaron en total | **Menor = mejor** |
| Contención % | % de la población que NO se infectó | **Mayor = mejor** (∼100%) |
| R0 estimado | Velocidad de propagación (< 1: se extingue) | **Menor = mejor** |
| Score compuesto [0–1] | Indicador global ponderado de las 5 métricas | **Mayor = mejor** |
| Victorias (solo lote) | En cuántas corridas fue la mejor estrategia | **Mayor = mejor** |

El reporte individual muestra las 11 primeras variables (sin "Victorias").
El reporte de lotes muestra las 12 (con "Victorias") gracias al parámetro
`esLote = true`.

Al pie de la tabla se incluye una nota sobre la dependencia de la topología de
red y por qué el modo por lotes ofrece un veredicto más robusto.

### 2. Integración en el flujo de exportación

En `GeneradorReportePDF.exportar`:
```java
agregarDesgloseScore(doc, resultados);
doc.newPage();
agregarGlosario(doc);   // nueva última página
```

En `GeneradorReporteLotePDF.exportar`:
```java
agregarDesgloseScore(doc, promedios);
doc.newPage();
agregarGlosario(doc, true);   // nueva última página (incluye "Victorias")
```

Los PDFs pasan de 7 páginas (individual) y N+2 páginas (lote) a **8 y N+3**
respectivamente.

### 3. Archivos modificados en v9

- `src/main/java/infrastructure/persistence/GeneradorReportePDF.java`
  (nuevo método `agregarGlosario`, llamada al final de `exportar`)
- `src/main/java/infrastructure/persistence/GeneradorReporteLotePDF.java`
  (nuevo método `agregarGlosario`, llamada al final de `exportar`)
- `README.md`
- `src/main/java/Context/01_contexto_proyecto.md`
- `src/main/java/Context/04_estructura_creada.md` (este archivo)
- `src/main/java/Context/05_grafos_y_algoritmos.md`

### 4. Pruebas realizadas
- `mvn compile`: BUILD SUCCESS sin errores ni warnings de compilación.
