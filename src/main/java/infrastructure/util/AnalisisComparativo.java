package infrastructure.util;

import application.dto.ResultadoSimulacionDto;
import domain.value.EstrategiaVacunacion;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calcula un score compuesto por estrategia y determina la más óptima.
 *
 * Las métricas se normalizan a [0, 1] y se invierten las que son "menor = mejor".
 * El score final pondera las cinco métricas del modelo (ver sección 7 de 03_modelo_matematico.md):
 *
 *   score = w_pico       · norm_inv(picoMaximoInfectados)
 *         + w_duracion   · norm_inv(duracionBrote)
 *         + w_afectados  · norm_inv(totalRecuperados)
 *         + w_contencion · norm(porcentajeContencion)
 *         + w_r0         · norm_inv(R0)
 *
 *   ∑ w = 1.0  →  score ∈ [0, 1]
 *
 * La estrategia con mayor score es la óptima desde el punto de vista de salud pública:
 * baja presión sobre el sistema, brote corto, pocos afectados, alta contención y R0 bajo.
 */
public class AnalisisComparativo {

    public static final double W_PICO       = 0.30;  // presión sobre sistema de salud
    public static final double W_DURACION   = 0.15;  // qué tan corto fue el brote
    public static final double W_AFECTADOS  = 0.25;  // cuántos enfermaron en total
    public static final double W_CONTENCION = 0.20;  // qué % no se infectó
    public static final double W_R0         = 0.10;  // velocidad inicial de propagación

    public static class ScoreEstrategia {
        public final EstrategiaVacunacion estrategia;
        public final double score;
        public final Map<String, Double> componentes; // contribución de cada métrica al score

        public ScoreEstrategia(EstrategiaVacunacion e, double score, Map<String, Double> comp) {
            this.estrategia  = e;
            this.score       = score;
            this.componentes = comp;
        }
    }

    /**
     * Calcula el score compuesto de cada estrategia y retorna la lista
     * ordenada de mayor a menor score (primer elemento = ganador).
     */
    public List<ScoreEstrategia> calcularRanking(List<ResultadoSimulacionDto> resultados) {
        if (resultados == null || resultados.isEmpty()) return List.of();

        // Valores máximos para normalizar
        int    maxPico       = resultados.stream().mapToInt(ResultadoSimulacionDto::getPicoMaximoInfectados).max().orElse(1);
        int    maxDuracion   = resultados.stream().mapToInt(ResultadoSimulacionDto::getDuracionBrote).max().orElse(1);
        int    maxAfectados  = resultados.stream().mapToInt(ResultadoSimulacionDto::getTotalRecuperados).max().orElse(1);
        double maxR0         = resultados.stream().mapToDouble(ResultadoSimulacionDto::getR0Estimado).max().orElse(1.0);
        double maxContencion = resultados.stream().mapToDouble(ResultadoSimulacionDto::getPorcentajeContencion).max().orElse(1.0);

        // Evitar división por cero
        if (maxPico       == 0) maxPico       = 1;
        if (maxDuracion   == 0) maxDuracion   = 1;
        if (maxAfectados  == 0) maxAfectados  = 1;
        if (maxR0         == 0) maxR0         = 1.0;
        if (maxContencion == 0) maxContencion = 1.0;

        final int maxP = maxPico, maxD = maxDuracion, maxA = maxAfectados;
        final double maxR = maxR0, maxC = maxContencion;

        java.util.List<ScoreEstrategia> ranking = new java.util.ArrayList<>();
        for (ResultadoSimulacionDto r : resultados) {
            double picoNormInv      = 1.0 - (double) r.getPicoMaximoInfectados() / maxP;
            double duracionNormInv  = 1.0 - (double) r.getDuracionBrote()        / maxD;
            double afectadosNormInv = 1.0 - (double) r.getTotalRecuperados()     / maxA;
            double contencionNorm   = r.getPorcentajeContencion() / maxC;
            double r0NormInv        = 1.0 - r.getR0Estimado()     / maxR;

            // Clamp por seguridad
            picoNormInv      = clamp(picoNormInv);
            duracionNormInv  = clamp(duracionNormInv);
            afectadosNormInv = clamp(afectadosNormInv);
            contencionNorm   = clamp(contencionNorm);
            r0NormInv        = clamp(r0NormInv);

            double cPico       = W_PICO       * picoNormInv;
            double cDuracion   = W_DURACION   * duracionNormInv;
            double cAfectados  = W_AFECTADOS  * afectadosNormInv;
            double cContencion = W_CONTENCION * contencionNorm;
            double cR0         = W_R0         * r0NormInv;

            double score = cPico + cDuracion + cAfectados + cContencion + cR0;

            Map<String, Double> comp = new LinkedHashMap<>();
            comp.put("Pico",       cPico);
            comp.put("Duración",   cDuracion);
            comp.put("Afectados",  cAfectados);
            comp.put("Contención", cContencion);
            comp.put("R0",         cR0);

            ranking.add(new ScoreEstrategia(r.getEstrategia(), score, comp));
        }

        ranking.sort((a, b) -> Double.compare(b.score, a.score));
        return ranking;
    }

    /**
     * Genera una justificación textual del por qué la estrategia ganadora es la óptima.
     */
    public String justificarGanador(List<ScoreEstrategia> ranking,
                                    List<ResultadoSimulacionDto> resultados) {
        if (ranking.isEmpty()) return "Sin resultados disponibles.";

        ScoreEstrategia ganador = ranking.get(0);
        ResultadoSimulacionDto rGanador = resultados.stream()
                .filter(r -> r.getEstrategia() == ganador.estrategia)
                .findFirst().orElse(null);
        if (rGanador == null) return "Estrategia ganadora: " + ganador.estrategia;

        Map<String, Double> comp = ganador.componentes;
        String fortaleza = comp.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("varias métricas");

        return String.format(
            "La estrategia %s obtuvo el mayor score compuesto (%.3f / 1.000).%n"
          + "Métricas obtenidas:%n"
          + "  • Pico máximo de infectados: %d%n"
          + "  • Duración del brote: %d turnos%n"
          + "  • Total de afectados: %d personas%n"
          + "  • Contención: %.1f%%%n"
          + "  • R0 estimado: %.2f%n"
          + "Su fortaleza principal proviene de la métrica: %s.",
          ganador.estrategia, ganador.score,
          rGanador.getPicoMaximoInfectados(),
          rGanador.getDuracionBrote(),
          rGanador.getTotalRecuperados(),
          rGanador.getPorcentajeContencion(),
          rGanador.getR0Estimado(),
          fortaleza);
    }

    private double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
