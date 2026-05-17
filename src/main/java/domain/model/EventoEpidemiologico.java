package domain.model;

import domain.value.TipoEvento;

/**
 * Evento de salud pública que modifica los pesos de todas las aristas del grafo
 * cuando el porcentaje de infectados supera el umbral definido.
 *
 * Cada evento solo se dispara una vez por simulación (yaDisparado evita reactivaciones).
 * GestorEventos evalúa la lista de eventos en cada turno.
 *
 * Fórmula de actualización de aristas al disparar:
 *   ∀ (u,v) ∈ E : w'(u,v) = w(u,v) × factorMultiplicador
 *
 * Eventos predefinidos:
 *   ALERTA_LEVE  | umbral=0.30 | factor=0.80
 *   CUARENTENA   | umbral=0.50 | factor=0.50
 *   LOCKDOWN     | umbral=0.70 | factor=0.20
 */
public class EventoEpidemiologico {

    private final TipoEvento tipo;
    private final double umbral;              // fracción de infectados que activa el evento (0.0–1.0)
    private final double factorMultiplicador; // se aplica a probContagio de todas las aristas
    private final String nombre;
    private boolean yaDisparado;             // garantiza que cada evento ocurra exactamente una vez

    public EventoEpidemiologico(TipoEvento tipo, double umbral,
                                double factorMultiplicador, String nombre) {
        this.tipo = tipo;
        this.umbral = umbral;
        this.factorMultiplicador = factorMultiplicador;
        this.nombre = nombre;
        this.yaDisparado = false;
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public TipoEvento getTipo() { return tipo; }
    public double getUmbral() { return umbral; }
    public double getFactorMultiplicador() { return factorMultiplicador; }
    public String getNombre() { return nombre; }
    public boolean estaDisparado() { return yaDisparado; }

    /**
     * Marca el evento como disparado. Llamado por GestorEventos cuando se cruza el umbral.
     * Después de llamar a este método, GestorEventos no volverá a procesarlo.
     */
    public void disparar() {
        this.yaDisparado = true;
    }

    @Override
    public String toString() {
        return String.format("Evento[%s | umbral=%.0f%% | factor=×%.2f | disparado=%b]",
                nombre, umbral * 100, factorMultiplicador, yaDisparado);
    }
}
