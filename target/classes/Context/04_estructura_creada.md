# Estructura del Proyecto — Cambios Aplicados

**Última actualización:** 2026-05-17

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

---

## Estructura de paquetes actual

```
src/main/java/
├── domain/
│   ├── model/        → package domain.model
│   ├── value/        → package domain.value
│   └── algoritmo/    → package domain.algoritmo
├── application/
│   ├── service/      → package application.service
│   ├── dto/          → package application.dto
│   └── command/      → package application.command
├── presentation/     → package presentation
└── infrastructure/
    ├── persistence/  → package infrastructure.persistence
    └── util/         → package infrastructure.util
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
