package domain.model;

/**
 * Arista dirigida del grafo. Representa que la persona {@code origen} puede
 * contagiar a la persona {@code destino} con probabilidad {@code probContagio}.
 *
 * El peso se calcula al crear la arista según los atributos demográficos del
 * origen y se almacena inmutablemente en {@code probContagioBase} ∈ [0.05, 0.95].
 *
 * En runtime, {@code probContagio} es el peso efectivo del turno actual.
 * {@code AjustadorPesosAdaptativo} lo recompone cada turno como:
 *   probContagio = clamp( probContagioBase × factorEventos × vigilanciaDestino × factorFatiga )
 */
public class Contacto {

    private final Persona origen;
    private final Persona destino;

    // Peso original calculado al crear la arista — nunca cambia.
    private final double probContagioBase;

    // Peso efectivo del turno actual. AjustadorPesosAdaptativo lo recalcula cada turno.
    private double probContagio;

    public Contacto(Persona origen, Persona destino, double probContagio) {
        this.origen = origen;
        this.destino = destino;
        this.probContagioBase = clamp(probContagio);
        this.probContagio     = this.probContagioBase;
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public Persona getOrigen()          { return origen; }
    public Persona getDestino()         { return destino; }
    public double getProbContagioBase() { return probContagioBase; }
    public double getProbContagio()     { return probContagio; }

    // ── Modificación de peso ───────────────────────────────────────────────────

    /** Establece la probabilidad directamente, manteniendo el rango [0.05, 0.95]. */
    public void setProbContagio(double probContagio) {
        this.probContagio = clamp(probContagio);
    }

    /**
     * Multiplica la probabilidad actual por {@code factor}.
     * Usado por GestorEventos al disparar un evento (ej.: CUARENTENA aplica × 0.50).
     */
    public void aplicarFactor(double factor) {
        setProbContagio(this.probContagio * factor);
    }

    // Mantiene el valor dentro del rango epidemiológicamente válido.
    private static double clamp(double valor) {
        return Math.max(0.05, Math.min(0.95, valor));
    }

    @Override
    public String toString() {
        return String.format("Contacto[%s → %s | prob=%.3f]",
                origen.getId(), destino.getId(), probContagio);
    }
}
