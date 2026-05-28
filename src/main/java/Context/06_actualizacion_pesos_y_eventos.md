# Actualización de pesos (probabilidad de contagio) y eventos epidemiológicos

Este documento explica cómo la probabilidad de contagio de cada arista del grafo cambia
durante la simulación según los eventos de salud pública y los dos estados conductuales
de la población: **alerta** (la gente se cuida más) y **relajación** (fatiga social).

---

## 1. El peso de una arista: base vs. efectivo

Cada arista `Contacto(u → v)` tiene **dos valores de probabilidad**:

| Campo | Clase | Descripción |
|---|---|---|
| `probContagioBase` | `Contacto.java` | Peso calculado al crear la arista. **Nunca cambia.** |
| `probContagio` | `Contacto.java` | Peso **efectivo del turno actual**. Se recalcula cada turno. |

```java
// Contacto.java — líneas 19-23
// Peso original calculado al crear la arista — nunca cambia.
private final double probContagioBase;

// Peso efectivo del turno actual. AjustadorPesosAdaptativo lo recalcula cada turno.
private double probContagio;
```

### Cómo se calcula el peso base al crear la red

`GeneradorPoblacion.calcularProbContagio()` toma los atributos demográficos del nodo **origen**
y multiplica tres factores sobre una probabilidad base de `0.20`:

```
probContagioBase = probBase × fEdad(u) × fEstrato(u) × fOcupacion(u)
```

| Factor | Fórmula | Rango |
|---|---|---|
| `fEdad` | `1.0 + (edad / 100)` | 1.00 (recién nacido) → 1.90 (90 años) |
| `fEstrato` | `1.0 + 0.1 × (6 − estrato)` | 1.00 (estrato 6) → 1.50 (estrato 1) |
| `fOcupacion` | salud=1.4 / informal=1.3 / estudiante=1.2 / empleado=1.0 / jubilado=0.8 | 0.80 → 1.40 |

Luego se aplica `clamp([0.05, 0.95])` para mantenerse en rango epidemiológicamente válido.

**Ejemplo:**
```
Persona P012, 45 años, estrato 2, trabajador de salud:
  fEdad     = 1.0 + 0.45 = 1.45
  fEstrato  = 1.0 + 0.1 × (6−2) = 1.40
  fOcupacion = 1.40
  probBase  = 0.20 × 1.45 × 1.40 × 1.40 = 0.568 → clamp → 0.568
```

Este valor queda fijo en `probContagioBase` para toda la simulación.

---

## 2. La fórmula de actualización cada turno

`AjustadorPesosAdaptativo.ajustar()` recompone el peso efectivo de **todas** las aristas
al final de cada turno aplicando tres factores sobre la base:

```
probEfectiva(u→v) = clamp( probContagioBase(u→v)
                            × factorEventos
                            × vigilancia(v)
                            × factorFatiga )
```

```java
// AjustadorPesosAdaptativo.java — línea 92
c.setProbContagio(c.getProbContagioBase() * factorEventos * vd * fatiga);
```

Los tres factores son independientes y se multiplican; cada uno modela un mecanismo
distinto de cambio de comportamiento colectivo.

---

## 3. Factor 1 — Eventos NPI (factorEventos)

### Qué son los NPI

Las Intervenciones No Farmacéuticas (NPI) son medidas de salud pública que se activan
automáticamente cuando la fracción de infectados cruza un umbral. Hay tres eventos
predefinidos, en orden creciente de severidad:

| Evento | Umbral (% infectados) | Factor multiplicador | Efecto neto |
|---|---|---|---|
| `ALERTA_LEVE` | 30% | × 0.80 | −20% de contagio |
| `CUARENTENA` | 50% | × 0.50 | −50% de contagio |
| `LOCKDOWN` | 70% | × 0.20 | −80% de contagio |

### Cómo se disparan

`GestorEventos.evaluar()` se llama **una vez por turno** con el estado actual de la red:

```java
// GestorEventos.java — líneas 62-84
public List<EventoEpidemiologico> evaluar(RedSocial red, int turnoActual) {
    int infectados = red.getPersonasPorEstado(EstadoSIRV.INFECTADO).size();
    double porcentaje = (double) infectados / total;

    for (EventoEpidemiologico evento : eventos) {
        if (!evento.estaDisparado() && porcentaje >= evento.getUmbral()) {
            factorAcumuladoEventos *= evento.getFactorMultiplicador();
            evento.disparar();   // se marca como disparado — no se repite
            disparados.add(evento);
        }
    }
    return disparados;
}
```

Cada evento **se dispara exactamente una vez** por simulación (flag `yaDisparado`).
Una vez activo, su factor queda acumulado de forma permanente.

### Factor acumulado

El `factorAcumuladoEventos` es el **producto** de todos los factores de eventos disparados
hasta el momento. Empieza en `1.0` y solo puede bajar:

```
Sin eventos:             1.00
Con ALERTA_LEVE:         1.00 × 0.80 = 0.80
Con ALERTA + CUARENTENA: 0.80 × 0.50 = 0.40
Con los tres:            0.40 × 0.20 = 0.08   (92% de reducción)
```

```java
// GestorEventos.java — línea 87
public double getFactorAcumuladoEventos() { return factorAcumuladoEventos; }
```

`AjustadorPesosAdaptativo` lee este valor una vez por turno y lo aplica a todas las aristas.

---

## 4. Factor 2 — Vigilancia local (estado de ALERTA por vecinos)

### Qué es la vigilancia

Cada nodo `v` ajusta la probabilidad de sus **aristas entrantes** según cuántos de sus
vecinos inmediatos están infectados. Si `v` está rodeado de infectados, `v` "se vuelve
más cuidadoso" y reduce su tasa de contacto efectivo.

```
vigilancia(v) = 1 − VIGILANCIA_MAX × (infectadosEntrantes(v) / totalEntrantes(v))
```

Constante: `VIGILANCIA_MAX = 0.60`

| Situación | Cálculo | Factor resultante |
|---|---|---|
| 0% de vecinos infectados | 1 − 0.60 × 0 = 1.00 | Sin reducción |
| 50% de vecinos infectados | 1 − 0.60 × 0.50 = 0.70 | −30% de contagio |
| 100% de vecinos infectados | 1 − 0.60 × 1.00 = 0.40 | −60% de contagio |

### Cómo se calcula

```java
// AjustadorPesosAdaptativo.java — líneas 113-137
private Map<Persona, Double> computarVigilancia(RedSocial red) {
    // Contar infectados entrantes y total entrantes por nodo destino
    for (Contacto c : red.getTodosLosContactos()) {
        int[] s = stats.get(c.getDestino());
        s[1]++;                                                    // total entrantes
        if (c.getOrigen().getEstado() == EstadoSIRV.INFECTADO)
            s[0]++;                                                // infectados entrantes
    }
    // Calcular factor: 1 − VIGILANCIA_MAX × fracción
    double fraccion = (double) s[0] / s[1];
    factor = 1.0 - VIGILANCIA_MAX * fraccion;
}
```

Este factor se recalcula **desde cero** cada turno — no es acumulativo.
A diferencia de `factorEventos`, este factor es **local**: cada nodo tiene su propio valor
dependiendo de su entorno inmediato en el grafo.

---

## 5. Factor 3 — Fatiga social (estado de RELAJACIÓN)

### Qué es la fatiga social

Cuando los infectados llevan varios turnos consecutivos sin crecer (la epidemia
está estabilizándose o bajando), la población "se relaja": pierde la precaución
y los pesos de las aristas **suben** (aumenta el contagio potencial).

Esto modela el fenómeno real de que las NPI son difíciles de mantener durante
períodos prolongados sin que la gente perciba un peligro inmediato.

### Parámetros

| Constante | Valor | Significado |
|---|---|---|
| `UMBRAL_FATIGA_TURNOS` | 5 | Turnos consecutivos sin crecimiento para activar fatiga |
| `FATIGA_MAX` | 0.30 | Máximo aumento del 30% sobre los pesos |
| `TURNOS_FATIGA_COMPLETA` | 10 | Turnos adicionales para llegar al máximo |

### Cómo funciona

```java
// AjustadorPesosAdaptativo.java — líneas 143-166
private CambioFatiga computarFatiga(List<Map<String, Integer>> historial) {
    int actualI = historial.get(historial.size() - 1).getOrDefault("I", 0);
    int prevI   = historial.get(historial.size() - 2).getOrDefault("I", 0);

    if (actualI <= prevI) {
        turnosDecreciendo++;                        // ← contador sube
        if (turnosDecreciendo == UMBRAL_FATIGA_TURNOS)
            return CambioFatiga.INICIADA;           // ← alerta en UI
    } else {
        turnosDecreciendo = 0;                      // ← se reinicia si vuelve a crecer
        if (estabaEnFatiga) return CambioFatiga.REINICIADA;
    }
}
```

El factor de fatiga que se aplica a las aristas:

```java
// AjustadorPesosAdaptativo.java — líneas 98-104
private double factorFatigaActual() {
    if (turnosDecreciendo < UMBRAL_FATIGA_TURNOS) return 1.0;  // sin fatiga
    int turnosEnFatiga = turnosDecreciendo - UMBRAL_FATIGA_TURNOS;
    double fatiga = Math.min(FATIGA_MAX,
            (double) turnosEnFatiga / TURNOS_FATIGA_COMPLETA * FATIGA_MAX);
    return 1.0 + fatiga;   // > 1.0 durante la fatiga (pesos SUBEN)
}
```

Progresión del factor de fatiga:

| `turnosDecreciendo` | Descripción | `factorFatiga` |
|---|---|---|
| 0–4 | Epidemia activa o recién estabilizada | 1.00 |
| 5 | Se activa la fatiga | 1.00 (recién iniciada) |
| 8 | Fatiga parcial | 1.09 |
| 15 | Fatiga completa | 1.30 (máximo) |
| ↑ nuevo crecimiento | Se reinicia | 1.00 |

### Interacción con los eventos NPI

La fatiga no cancela los eventos NPI. Si `CUARENTENA` está activa (`factorEventos = 0.40`)
y la fatiga está al máximo (`factorFatiga = 1.30`), el efecto neto es:

```
probEfectiva = base × 0.40 × vigilancia(v) × 1.30
             = base × 0.52 × vigilancia(v)
```

Es decir, la cuarentena sigue reduciendo las probabilidades, pero la fatiga social
"erosiona" parte de ese beneficio.

---

## 6. Los dos estados conductuales: ALERTA y RELAJACIÓN

El sistema modela dos estados opuestos de comportamiento colectivo:

### Estado ALERTA

Se activa cuando la epidemia crece o cuando se dispara un evento NPI. Reduce los pesos.

**Señales que lo producen:**
- Cualquier evento NPI disparado → `factorEventos` baja (permanente).
- Nodos rodeados de infectados → `vigilancia(v)` baja (local, turno a turno).
- Epidemia volviendo a crecer → `turnosDecreciendo = 0`, fatiga se reinicia.

```
Estado ALERTA:
  factorEventos ∈ (0, 1]       — cuánto han intervenido los NPI
  vigilancia(v) ∈ [0.40, 1.00] — cuánto se cuida el nodo v
  factorFatiga = 1.0            — sin relajación
```

### Estado RELAJACIÓN (fatiga social)

Se activa cuando la epidemia lleva ≥ 5 turnos sin crecer.

**Señales que lo producen:**
- `turnosDecreciendo >= UMBRAL_FATIGA_TURNOS = 5`.
- Los infectados de hoy son ≤ los de ayer durante varios turnos consecutivos.

```
Estado RELAJACIÓN:
  factorEventos ∈ (0, 1]       — los NPI siguen activos
  vigilancia(v) ≈ 1.0           — pocos vecinos infectados → nadie se cuida
  factorFatiga ∈ (1.0, 1.30]   — pesos suben hasta un 30%
```

### Diagrama de transición

```
              infectados crecen (o es el primer turno)
              ────────────────────────────────────────►
  [RELAJACIÓN]                                        [ALERTA]
  factorFatiga > 1.0                                  factorFatiga = 1.0
  turnosDecreciendo = 0  ◄──────────────────────────  turnosDecreciendo++
              infectados estables/bajando ≥ 5 turnos
```

---

## 7. Orden de operaciones por turno

`SimulacionService.ejecutar()` orquesta el loop. El orden importa porque cada paso
lee el resultado del anterior:

```
Turno t:
  1. ModeloSIRV.simularTurno(red)
       → propaga infección usando probContagio efectiva del turno anterior
       → actualiza estados S/I/R de los nodos

  2. GestorEventos.evaluar(red, t)
       → comprueba si algún umbral fue superado
       → si sí: factorAcumuladoEventos × = factorEvento
       → retorna lista de eventos disparados (para alertas en UI)

  3. historial.add(conteo)
       → registra {S, I, R, V} del turno t para el cálculo de fatiga

  4. AjustadorPesosAdaptativo.ajustar(red, factorEventos, historial)
       → computarVigilancia(): factor local por nodo
       → computarFatiga(): actualiza turnosDecreciendo
       → para cada arista: probContagio = base × factorEventos × vigilancia × fatiga
       → retorna CambioFatiga (para alerta en UI)

  5. GraficoSimulacion.actualizarTurno() (opcional, si hay UI)
       → muestra alertas de eventos y fatiga
       → repinta colores de nodos
```

```java
// SimulacionService.java — líneas 68-88
for (int t = 1; t <= config.getTurnosMaximos(); t++) {
    Map<String, Integer> conteo = modelo.simularTurno(red);          // paso 1
    List<EventoEpidemiologico> disparados = gestor.evaluar(red, t);  // paso 2
    historial.add(conteo);                                           // paso 3
    CambioFatiga cambioFatiga =
        ajustador.ajustar(red, gestor.getFactorAcumuladoEventos(), historial); // paso 4
    // paso 5...
}
```

---

## 8. Interacción entre los tres factores — ejemplos concretos

### Ejemplo A — Epidemia en pico (turno 12)

- 60% de los vecinos de `v` están infectados.
- `CUARENTENA` ya fue disparada (`factorEventos = 0.40`).
- Epidemia sigue creciendo → `turnosDecreciendo = 0` → `factorFatiga = 1.0`.

```
probEfectiva(u→v) = 0.50 × 0.40 × (1.0 − 0.60×0.60) × 1.0
                  = 0.50 × 0.40 × 0.64 × 1.0
                  = 0.128   (−74% respecto a base)
```

### Ejemplo B — Epidemia en declive prolongado (turno 35)

- 5% de los vecinos de `v` infectados (epidemia bajando).
- Los tres NPI disparados (`factorEventos = 0.08`).
- 15 turnos sin crecimiento → `factorFatiga = 1.30`.

```
probEfectiva(u→v) = 0.50 × 0.08 × (1.0 − 0.60×0.05) × 1.30
                  = 0.50 × 0.08 × 0.97 × 1.30
                  = 0.0505   (−90% respecto a base, pero la fatiga erosionó parte del beneficio)
```

### Ejemplo C — Rebote después de relajación

Si en el ejemplo B los infectados vuelven a crecer:
- `turnosDecreciendo` se reinicia a `0`.
- `factorFatiga` vuelve a `1.0`.
- `factorEventos` **no cambia** — los NPI ya disparados son permanentes.

```
probEfectiva(u→v) = 0.50 × 0.08 × vigilancia(v) × 1.0
```

La epidemia rebota con pesos más bajos que al inicio, porque los NPI siguen activos.

---

## 9. Clases y responsabilidades

| Clase | Paquete | Responsabilidad |
|---|---|---|
| `EventoEpidemiologico` | `domain.model` | Contiene umbral, factor y flag `yaDisparado` de un evento NPI |
| `TipoEvento` | `domain.value` | Enum con los tres tipos: `ALERTA_LEVE`, `CUARENTENA`, `LOCKDOWN` |
| `GestorEventos` | `domain.algoritmo` | Evalúa umbrales y mantiene `factorAcumuladoEventos` |
| `AjustadorPesosAdaptativo` | `domain.algoritmo` | Recalcula `probContagio` de cada arista usando los tres factores |
| `Contacto` | `domain.model` | Almacena `probContagioBase` (fijo) y `probContagio` (efectivo) |
| `Persona` | `domain.model` | Provee `factorEdad()`, `factorEstrato()`, `factorOcupacion()` |
| `ModeloSIRV` | `domain.algoritmo` | Usa `probContagio` efectivo para propagar la infección cada turno |
| `SimulacionService` | `application.service` | Orquesta el orden: simular → evaluar → historial → ajustar |

---

## 10. Resumen de constantes del sistema

| Constante | Valor | Clase | Qué controla |
|---|---|---|---|
| `PROB_BASE_DEFAULT` | 0.20 | `GeneradorPoblacion` | Probabilidad base antes de factores demográficos |
| Rango clamp | [0.05, 0.95] | `Contacto` | Límites mínimo/máximo de cualquier peso |
| `VIGILANCIA_MAX` | 0.60 | `AjustadorPesosAdaptativo` | Máxima reducción por reacción local (−60%) |
| `UMBRAL_FATIGA_TURNOS` | 5 | `AjustadorPesosAdaptativo` | Turnos sin crecimiento para activar fatiga |
| `FATIGA_MAX` | 0.30 | `AjustadorPesosAdaptativo` | Máximo aumento por fatiga social (+30%) |
| `TURNOS_FATIGA_COMPLETA` | 10 | `AjustadorPesosAdaptativo` | Turnos adicionales para alcanzar `FATIGA_MAX` |
| Factor `ALERTA_LEVE` | 0.80 | `GestorEventos` | −20% al cruzar el 30% de infectados |
| Factor `CUARENTENA` | 0.50 | `GestorEventos` | −50% al cruzar el 50% de infectados |
| Factor `LOCKDOWN` | 0.20 | `GestorEventos` | −80% al cruzar el 70% de infectados |
