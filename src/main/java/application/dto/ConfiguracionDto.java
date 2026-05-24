package application.dto;

import domain.value.EstrategiaVacunacion;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Configuración de una simulación, construida por ConsolaMenu y
 * consumida por IniciarSimulacionCommand.
 *
 * La semilla aleatoria se genera automáticamente en cada nueva sesión y es interna —
 * no se pide al usuario. Se mantiene como campo para reproducibilidad dentro del
 * mismo comparativo (todas las estrategias usan la misma semilla en una corrida).
 */
public class ConfiguracionDto {

    /** Fracción de la población que arranca infectada (paciente cero). */
    public static final double FRACCION_INFECTADOS_INICIALES = 0.15;

    private int tamanoRed;
    private int turnosMaximos;
    private EstrategiaVacunacion estrategia;
    private long semillaAleatoria;
    private double probInfeccionBase;
    private int diasRecuperacion;
    private int cantidadPacientesCero;
    private boolean usarCSV;
    private String archivoPersonas;
    private String archivoContactos;
    private boolean mostrarVisualizacion;
    private int pausaVisualizacionMs;

    public ConfiguracionDto() {
        tamanoRed             = 80;
        turnosMaximos         = 60;
        estrategia            = EstrategiaVacunacion.ALEATORIA;
        semillaAleatoria      = ThreadLocalRandom.current().nextLong(); // automática
        probInfeccionBase     = 0.20;
        diasRecuperacion      = 7;
        cantidadPacientesCero = calcularInfectadosIniciales(tamanoRed);
        usarCSV               = false;
        archivoPersonas       = "data/personas_red1.csv";
        archivoContactos      = "data/contactos_red1.csv";
        mostrarVisualizacion  = true;
        pausaVisualizacionMs  = 1000;
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public int getTamanoRed()                   { return tamanoRed; }
    public int getTurnosMaximos()               { return turnosMaximos; }
    public EstrategiaVacunacion getEstrategia() { return estrategia; }
    public long getSemillaAleatoria()           { return semillaAleatoria; }
    public double getProbInfeccionBase()        { return probInfeccionBase; }
    public int getDiasRecuperacion()            { return diasRecuperacion; }
    public int getCantidadPacientesCero()       { return cantidadPacientesCero; }
    public boolean isUsarCSV()                  { return usarCSV; }
    public String getArchivoPersonas()          { return archivoPersonas; }
    public String getArchivoContactos()         { return archivoContactos; }
    public boolean isMostrarVisualizacion()     { return mostrarVisualizacion; }
    public int getPausaVisualizacionMs()        { return pausaVisualizacionMs; }

    // ── Setters ────────────────────────────────────────────────────────────────

    /**
     * Al fijar el tamaño de red se recalcula el paciente cero al 15% de la población,
     * de modo que tanto la UI Swing como la consola y el comparativo lo apliquen sin
     * duplicar la regla. Para forzar un valor distinto, llamar a
     * {@link #setCantidadPacientesCero(int)} después de este método.
     */
    public void setTamanoRed(int v) {
        this.tamanoRed = v;
        this.cantidadPacientesCero = calcularInfectadosIniciales(v);
    }

    private static int calcularInfectadosIniciales(int n) {
        return Math.max(1, (int) Math.round(FRACCION_INFECTADOS_INICIALES * n));
    }
    public void setTurnosMaximos(int v)                  { this.turnosMaximos = v; }
    public void setEstrategia(EstrategiaVacunacion v)    { this.estrategia = v; }
    public void setSemillaAleatoria(long v)              { this.semillaAleatoria = v; }
    public void setProbInfeccionBase(double v)           { this.probInfeccionBase = v; }
    public void setDiasRecuperacion(int v)               { this.diasRecuperacion = v; }
    public void setCantidadPacientesCero(int v)          { this.cantidadPacientesCero = v; }
    public void setUsarCSV(boolean v)                    { this.usarCSV = v; }
    public void setArchivoPersonas(String v)             { this.archivoPersonas = v; }
    public void setArchivoContactos(String v)            { this.archivoContactos = v; }
    public void setMostrarVisualizacion(boolean v)       { this.mostrarVisualizacion = v; }
    public void setPausaVisualizacionMs(int v)           { this.pausaVisualizacionMs = v; }

    @Override
    public String toString() {
        return String.format("Config[N=%d | turnos=%d | estrategia=%s | probBase=%.2f | diasRec=%d]",
                tamanoRed, turnosMaximos, estrategia, probInfeccionBase, diasRecuperacion);
    }
}
