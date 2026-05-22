package domain.model;

import domain.value.EstadoSIRV;
import java.util.Objects;

/**
 * Nodo del grafo. Representa una persona con atributos demográficos colombianos.
 *
 * Sus atributos influyen directamente en el peso de las aristas adyacentes:
 *   probContagio = probBase × fEdad(u) × fEstrato(u) × fOcupacion(u)
 *
 * Factores:
 *   fEdad(u)      = 1.0 + (edad / 100)          — mayor edad, mayor vulnerabilidad
 *   fEstrato(u)   = 1.0 + 0.1 × (6 - estrato)   — estrato bajo, mayor exposición
 *   fOcupacion(u) = salud:1.4 | informal:1.3 | estudiante:1.2 | empleado:1.0 | jubilado:0.8
 *
 * Identidad basada en id para que funcione correctamente como clave del HashMap<Persona, ...>.
 */
public class Persona {

    private final String id;       // identificador único, inmutable
    private int edad;              // 0–90, determina fEdad
    private int estrato;           // 1–6, determina fEstrato
    private String ocupacion;      // determina fOcupacion al calcular peso de arista
    private EstadoSIRV estado;
    private int diasInfectado;     // se incrementa cada turno; llegar a diasRecuperacion → RECUPERADO

    public Persona(String id, int edad, int estrato, String ocupacion) {
        this.id = id;
        this.edad = edad;
        this.estrato = estrato;
        this.ocupacion = ocupacion;
        this.estado = EstadoSIRV.SUSCEPTIBLE;
        this.diasInfectado = 0;
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public int getEdad() { return edad; }
    public int getEstrato() { return estrato; }
    public String getOcupacion() { return ocupacion; }
    public EstadoSIRV getEstado() { return estado; }
    public int getDiasInfectado() { return diasInfectado; }

    // ── Setters ────────────────────────────────────────────────────────────────

    public void setEdad(int edad) { this.edad = edad; }
    public void setEstrato(int estrato) { this.estrato = estrato; }
    public void setOcupacion(String ocupacion) { this.ocupacion = ocupacion; }
    public void setEstado(EstadoSIRV estado) { this.estado = estado; }
    public void setDiasInfectado(int diasInfectado) { this.diasInfectado = diasInfectado; }

    /** Incrementa el contador de días infectado. Llamado por ModeloSIRV al final de cada turno. */
    public void incrementarDiasInfectado() { this.diasInfectado++; }

    // ── Factores de riesgo demográfico ─────────────────────────────────────────
    // Usados por GeneradorPoblacion.calcularProbContagio() al crear cada arista.
    // La fórmula completa es: probContagio = probBase × fEdad × fEstrato × fOcupacion

    /**
     * Factor por edad del nodo origen.
     * A mayor edad, mayor vulnerabilidad como fuente de contagio.
     *
     * fEdad = 1.0 + (edad / 100.0)
     *   edad=0  → 1.00   (mínimo riesgo)
     *   edad=50 → 1.50
     *   edad=90 → 1.90   (máximo riesgo)
     */
    public double factorEdad() {
        return 1.0 + (edad / 100.0);
    }

    /**
     * Factor por estrato socioeconómico.
     * Estrato bajo implica mayor exposición: hacinamiento, transporte público masivo.
     *
     * fEstrato = 1.0 + 0.1 × (6 - estrato)
     *   estrato 1 → 1.50   (máxima exposición)
     *   estrato 3 → 1.30
     *   estrato 6 → 1.00   (mínima exposición)
     */
    public double factorEstrato() {
        return 1.0 + 0.1 * (6 - estrato);
    }

    /**
     * Factor por ocupación.
     * Refleja el nivel de contacto social propio de cada tipo de trabajo.
     *
     *   salud      → 1.40  (exposición directa a enfermos, alta densidad de contacto)
     *   informal   → 1.30  (vendedor callejero, mucho contacto sin protección)
     *   estudiante → 1.20  (clusters escolares densamente conectados)
     *   empleado   → 1.00  (contacto moderado en entorno controlado) ← default
     *   jubilado   → 0.80  (pocas salidas, menor interacción social)
     */
    public double factorOcupacion() {
        return switch (ocupacion.toLowerCase()) {
            case "salud"      -> 1.40;
            case "informal"   -> 1.30;
            case "estudiante" -> 1.20;
            case "jubilado"   -> 0.80;
            default           -> 1.00; // empleado u ocupación no reconocida
        };
    }

    // ── Identidad basada en id ─────────────────────────────────────────────────
    // Imprescindible para que Persona funcione como clave en HashMap<Persona, List<Contacto>>.

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Persona)) return false;
        return Objects.equals(id, ((Persona) o).id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return String.format("Persona[%s | edad=%d | estrato=%d | %s | %s | diasInf=%d]",
                id, edad, estrato, ocupacion, estado, diasInfectado);
    }
}
