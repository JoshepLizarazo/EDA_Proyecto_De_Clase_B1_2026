package infrastructure.util;

import application.dto.ResultadoSimulacionDto;
import domain.value.EstrategiaVacunacion;
import java.util.List;
import java.util.Map;

/**
 * Calcula métricas de análisis a partir del historial turno a turno de una simulación.
 */
public class CalculadorEstadisticas {

    /**
     * Construye el ResultadoSimulacionDto con todas las métricas calculadas.
     */
    public ResultadoSimulacionDto construirResultado(EstrategiaVacunacion estrategia,
                                                     List<Map<String, Integer>> historial,
                                                     int totalPoblacion) {
        int pico      = calcularPicoMaximo(historial);
        int turnoPico = calcularTurnoPico(historial);
        int duracion  = calcularDuracionBrote(historial);
        int recuperados = historial.isEmpty() ? 0
                : historial.get(historial.size() - 1).getOrDefault("R", 0);
        int vacunados = historial.isEmpty() ? 0
                : historial.get(0).getOrDefault("V", 0);
        double r0 = calcularR0(historial);

        return new ResultadoSimulacionDto(estrategia, historial, pico, turnoPico,
                duracion, recuperados, vacunados, totalPoblacion, r0);
    }

    // ── Métricas ──────────────────────────────────────────────────────────────

    /** max{ I(t) } — pico máximo de infectados. */
    private int calcularPicoMaximo(List<Map<String, Integer>> historial) {
        return historial.stream()
                .mapToInt(m -> m.getOrDefault("I", 0))
                .max().orElse(0);
    }

    /** Turno t en que ocurrió el pico máximo. */
    private int calcularTurnoPico(List<Map<String, Integer>> historial) {
        int pico = calcularPicoMaximo(historial);
        for (int t = 0; t < historial.size(); t++) {
            if (historial.get(t).getOrDefault("I", 0) == pico) return t;
        }
        return 0;
    }

    /** Número de turnos hasta que I(t) = 0. */
    private int calcularDuracionBrote(List<Map<String, Integer>> historial) {
        for (int t = historial.size() - 1; t >= 0; t--) {
            if (historial.get(t).getOrDefault("I", 0) > 0) return t + 1;
        }
        return 0;
    }

    /**
     * R0 estimado: promedio de nuevos infectados por infectado activo en los primeros 3 turnos.
     * R0 = (I(t) - I(t-1)) / I(t-1) promediado en t=1,2,3.
     */
    private double calcularR0(List<Map<String, Integer>> historial) {
        if (historial.size() < 2) return 0.0;
        double suma = 0.0;
        int count   = 0;
        int limite  = Math.min(4, historial.size());
        for (int t = 1; t < limite; t++) {
            int prev = historial.get(t - 1).getOrDefault("I", 0);
            int curr = historial.get(t).getOrDefault("I", 0);
            if (prev > 0) {
                suma += (double) curr / prev;
                count++;
            }
        }
        return count > 0 ? suma / count : 0.0;
    }
}
