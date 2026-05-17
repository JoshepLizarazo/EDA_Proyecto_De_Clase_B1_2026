package application.dto;

import domain.value.EstrategiaVacunacion;
import java.util.List;
import java.util.Map;

/**
 * Resultado completo de una simulación: historial turno a turno y métricas finales.
 * Construido por CalculadorEstadisticas al finalizar SimulacionService.
 */
public class ResultadoSimulacionDto {

    private final EstrategiaVacunacion estrategia;
    private final List<Map<String, Integer>> historialPorTurno;
    private final int picoMaximoInfectados;
    private final int turnoDePico;
    private final int duracionBrote;
    private final int totalRecuperados;
    private final int totalVacunados;
    private final int totalPoblacion;
    private final double r0Estimado;
    private final double porcentajeContencion;

    public ResultadoSimulacionDto(EstrategiaVacunacion estrategia,
                                  List<Map<String, Integer>> historialPorTurno,
                                  int picoMaximoInfectados, int turnoDePico,
                                  int duracionBrote, int totalRecuperados,
                                  int totalVacunados, int totalPoblacion,
                                  double r0Estimado) {
        this.estrategia           = estrategia;
        this.historialPorTurno    = historialPorTurno;
        this.picoMaximoInfectados = picoMaximoInfectados;
        this.turnoDePico          = turnoDePico;
        this.duracionBrote        = duracionBrote;
        this.totalRecuperados     = totalRecuperados;
        this.totalVacunados       = totalVacunados;
        this.totalPoblacion       = totalPoblacion;
        this.r0Estimado           = r0Estimado;
        this.porcentajeContencion = totalPoblacion > 0
                ? (totalPoblacion - totalRecuperados) * 100.0 / totalPoblacion
                : 0.0;
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public EstrategiaVacunacion getEstrategia()              { return estrategia; }
    public List<Map<String, Integer>> getHistorialPorTurno() { return historialPorTurno; }
    public int getPicoMaximoInfectados()                     { return picoMaximoInfectados; }
    public int getTurnoDePico()                              { return turnoDePico; }
    public int getDuracionBrote()                            { return duracionBrote; }
    public int getTotalRecuperados()                         { return totalRecuperados; }
    public int getTotalVacunados()                           { return totalVacunados; }
    public int getTotalPoblacion()                           { return totalPoblacion; }
    public double getR0Estimado()                            { return r0Estimado; }
    public double getPorcentajeContencion()                  { return porcentajeContencion; }

    @Override
    public String toString() {
        return String.format(
            "%-12s | Pico: %3d (t=%2d) | Duración: %2d | Recuperados: %3d | Contención: %5.1f%% | R0≈%.2f",
            estrategia, picoMaximoInfectados, turnoDePico,
            duracionBrote, totalRecuperados, porcentajeContencion, r0Estimado);
    }
}
