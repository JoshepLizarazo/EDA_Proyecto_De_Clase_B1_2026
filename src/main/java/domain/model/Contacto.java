package domain.model;

/**
 * Arista dirigida del grafo. Representa que la persona {@code origen} puede
 * contagiar a la persona {@code destino} con probabilidad {@code probContagio}.
 *
 * El peso (probContagio) se calcula al crear la arista según los atributos
 * demográficos de origen y se mantiene en [0.05, 0.95].
 *
 * En runtime, GestorEventos puede reducir este peso multiplicándolo por
 * el factorMultiplicador del evento activo (ej.: × 0.50 en cuarentena).
 */
public class Contacto {

    private final Persona origen;
    private final Persona destino;

    // Probabilidad de que origen contagie a destino en un turno. Rango: [0.05, 0.95].
    // GestorEventos la reduce al disparar un evento epidemiológico.
    private double probContagio;

    public Contacto(Persona origen, Persona destino, double probContagio) {
        this.origen = origen;
        this.destino = destino;
        this.probContagio = clamp(probContagio);
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public Persona getOrigen() { return origen; }
    public Persona getDestino() { return destino; }
    public double getProbContagio() { return probContagio; }

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
