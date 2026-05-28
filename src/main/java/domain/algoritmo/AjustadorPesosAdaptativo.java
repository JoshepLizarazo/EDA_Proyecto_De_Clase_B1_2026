package domain.algoritmo;

import domain.model.Contacto;
import domain.model.Persona;
import domain.model.RedSocial;
import domain.value.EstadoSIRV;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Recompone el peso efectivo de TODAS las aristas en cada turno combinando tres factores:
 *
 *   probEfectiva(u→v) = clamp( base(u→v) × factorEventos × vigilancia(v) × factorFatiga )
 *
 * ── Factor 1: eventos NPI (factorEventos) ──────────────────────────────────────────────
 *   Producto acumulado de los factores de los eventos de salud pública ya disparados
 *   (ALERTA_LEVE × CUARENTENA × LOCKDOWN). Calculado por GestorEventos y pasado como
 *   parámetro. Reduce los pesos de forma permanente dentro de la simulación.
 *
 * ── Factor 2: reacción local (vigilancia(v)) ───────────────────────────────────────────
 *   Cada nodo v reduce la probabilidad de SUS ARISTAS ENTRANTES según la fracción
 *   de sus vecinos entrantes que están infectados. Si v está rodeado de infectados,
 *   v "se vuelve más cuidadoso": baja su tasa de contacto efectiva.
 *
 *     vigilancia(v) = 1 − VIGILANCIA_MAX × (infectadosEntrantes(v) / totalEntrantes(v))
 *     VIGILANCIA_MAX = 0.60
 *
 *   Con el 100% de vecinos infectados: vigilancia = 0.40 (reducción máxima del 60%).
 *   Con 0% de vecinos infectados:      vigilancia = 1.00 (sin reducción).
 *
 *   Este factor se recalcula desde cero cada turno; no es acumulativo entre turnos.
 *
 * ── Factor 3: fatiga social (factorFatiga) ─────────────────────────────────────────────
 *   Si el número de infectados lleva UMBRAL_FATIGA_TURNOS turnos consecutivos sin
 *   crecer, la gente "se relaja" y los pesos suben gradualmente (máximo FATIGA_MAX = 30%).
 *   Cuando la epidemia vuelve a crecer, el contador se reinicia.
 *
 *     si turnosDecreciendo >= UMBRAL:
 *       fatiga = min(FATIGA_MAX, (turnosDecreciendo − UMBRAL) / TURNOS_COMPLETO × FATIGA_MAX)
 *       factorFatiga = 1.0 + fatiga      (> 1.0 durante la fatiga)
 *     si no:
 *       factorFatiga = 1.0
 *
 *   Este factor modela el "efecto memoria": los NPI previos aún mantienen algún efecto
 *   (factorEventos < 1) pero la gente se vuelve más laxa conforme el peligro parece menor.
 *
 * ── Composición de factores ────────────────────────────────────────────────────────────
 *   Los tres factores se multiplican. Ejemplos:
 *
 *   Epidemia en pico (60% vecinos infectados, sin NPI aún, sin fatiga):
 *     prob = 0.50 × 1.0 × 0.64 × 1.0 = 0.32   (−36%)
 *
 *   Epidemia en declive largo (CUARENTENA activa, 0 vecinos infectados, fatiga al máx):
 *     prob = 0.50 × 0.40 × 1.0 × 1.30 = 0.26  (−48% vs. base, +30% vs. post-cuarentena)
 *
 * @see GestorEventos que calcula factorAcumuladoEventos
 * @see SimulacionService que invoca ajustar() cada turno tras evaluar() e historial
 */
public class AjustadorPesosAdaptativo {

    /** Describe si el estado de la fatiga social cambió en el último turno. */
    public enum CambioFatiga { NINGUNO, INICIADA, REINICIADA }

    // Reacción local
    private static final double VIGILANCIA_MAX         = 0.60;

    // Fatiga social
    private static final int    UMBRAL_FATIGA_TURNOS   = 5;
    private static final double FATIGA_MAX             = 0.30;
    private static final int    TURNOS_FATIGA_COMPLETA = 10;

    private int turnosDecreciendo = 0;

    /**
     * Recomputa probContagio de cada arista para el próximo turno.
     * Debe llamarse DESPUÉS de GestorEventos.evaluar() y DESPUÉS de añadir el
     * conteo actual al historial para que la fatiga tenga la última lectura de I(t).
     *
     * @param red           grafo con los estados de los nodos ya actualizados en este turno
     * @param factorEventos producto acumulado de factores NPI (de GestorEventos)
     * @param historial     serie temporal de conteos {S,I,R,V}; el último elemento es el turno actual
     * @return {@code CambioFatiga} indicando si la fatiga se inició o reinició en este turno
     */
    public CambioFatiga ajustar(RedSocial red, double factorEventos, List<Map<String, Integer>> historial) {
        Map<Persona, Double> vigilancia = computarVigilancia(red);
        CambioFatiga cambio = computarFatiga(historial);
        double fatiga = factorFatigaActual();

        for (Contacto c : red.getTodosLosContactos()) {
            double vd = vigilancia.getOrDefault(c.getDestino(), 1.0);
            c.setProbContagio(c.getProbContagioBase() * factorEventos * vd * fatiga);
        }
        return cambio;
    }

    /** Factor de fatiga del turno actual (> 1.0 cuando hay relajación activa). */
    private double factorFatigaActual() {
        if (turnosDecreciendo < UMBRAL_FATIGA_TURNOS) return 1.0;
        int turnosEnFatiga = turnosDecreciendo - UMBRAL_FATIGA_TURNOS;
        double fatiga = Math.min(FATIGA_MAX,
                (double) turnosEnFatiga / TURNOS_FATIGA_COMPLETA * FATIGA_MAX);
        return 1.0 + fatiga;
    }

    /**
     * Para cada nodo v calcula la fracción de sus vecinos entrantes que están infectados
     * y devuelve el factor de vigilancia correspondiente (∈ [1−VIGILANCIA_MAX, 1.0]).
     *
     * La vigilancia se aplica a las aristas ENTRANTES de v: v se protege de sus contactos
     * infectados reduciendo la probabilidad de que cualquiera de ellos lo contagie.
     */
    private Map<Persona, Double> computarVigilancia(RedSocial red) {
        // stats[v] = [infectadosEntrantes, totalEntrantes]
        Map<Persona, int[]> stats = new HashMap<>();
        for (Persona p : red.getTodasLasPersonas()) {
            stats.put(p, new int[]{0, 0});
        }
        for (Contacto c : red.getTodosLosContactos()) {
            int[] s = stats.get(c.getDestino());
            if (s == null) continue;
            s[1]++;
            if (c.getOrigen().getEstado() == EstadoSIRV.INFECTADO) s[0]++;
        }

        Map<Persona, Double> factores = new HashMap<>();
        for (Map.Entry<Persona, int[]> entry : stats.entrySet()) {
            int[] s = entry.getValue();
            double factor = 1.0;
            if (s[1] > 0) {
                double fraccion = (double) s[0] / s[1];
                factor = 1.0 - VIGILANCIA_MAX * fraccion;
            }
            factores.put(entry.getKey(), factor);
        }
        return factores;
    }

    /**
     * Actualiza el contador de turnos en descenso y retorna si el estado de la
     * fatiga cambió en este turno (útil para que la UI muestre una alerta).
     */
    private CambioFatiga computarFatiga(List<Map<String, Integer>> historial) {
        if (historial.size() < 2) return CambioFatiga.NINGUNO;

        int actualI = historial.get(historial.size() - 1).getOrDefault("I", 0);
        int prevI   = historial.get(historial.size() - 2).getOrDefault("I", 0);

        if (actualI <= prevI) {
            turnosDecreciendo++;
            if (turnosDecreciendo == UMBRAL_FATIGA_TURNOS) {
                System.out.printf("  [Fatiga social] Infectados estables/bajando x%d turnos "
                        + "— precaución se relaja (pesos suben hasta +%.0f%%)%n",
                        UMBRAL_FATIGA_TURNOS, FATIGA_MAX * 100);
                return CambioFatiga.INICIADA;
            }
        } else {
            boolean estabaEnFatiga = turnosDecreciendo >= UMBRAL_FATIGA_TURNOS;
            turnosDecreciendo = 0;
            if (estabaEnFatiga) {
                System.out.println("  [Fatiga social] Nueva alza de infectados "
                        + "— precaución restaurada");
                return CambioFatiga.REINICIADA;
            }
        }
        return CambioFatiga.NINGUNO;
    }
}
