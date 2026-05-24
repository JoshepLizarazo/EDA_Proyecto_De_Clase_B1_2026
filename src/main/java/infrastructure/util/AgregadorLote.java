package infrastructure.util;

import application.dto.ResultadoLoteDto;
import application.dto.ResultadoSimulacionDto;
import domain.value.EstrategiaVacunacion;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agrega los resultados de un experimento por lotes.
 *
 * Como cada estrategia corre sobre grafos distintos, la comparación se hace de
 * dos formas complementarias:
 *
 *   1. PROMEDIO: se promedian las métricas de las N corridas de cada estrategia
 *      (pico, turno-pico, duración, recuperados, vacunados, R0) y se construye una
 *      curva I(t) promedio. Sobre esos promedios se aplica el mismo score compuesto
 *      del comparativo normal ({@link AnalisisComparativo}).
 *
 *   2. ACUMULADO (victorias): para cada índice de corrida i se toma el resultado
 *      i-ésimo de cada estrategia, se rankea por score compuesto y se cuenta una
 *      victoria para la mejor. Es una estimación Monte Carlo de qué tan seguido
 *      cada estrategia resulta la mejor sobre grafos aleatorios.
 */
public class AgregadorLote {

    private final AnalisisComparativo analisis = new AnalisisComparativo();

    /**
     * @param porEstrategia una lista por estrategia; cada elemento es la lista de
     *                      N resultados (uno por grafo) de esa estrategia, en orden de corrida.
     * @param nGrafos       número de grafos por estrategia
     */
    public ResultadoLoteDto agregar(List<List<ResultadoSimulacionDto>> porEstrategia, int nGrafos) {
        List<ResultadoSimulacionDto> promedios = new ArrayList<>();
        for (List<ResultadoSimulacionDto> corridas : porEstrategia) {
            if (!corridas.isEmpty()) promedios.add(promediar(corridas));
        }
        Map<EstrategiaVacunacion, Integer> victorias = contarVictorias(porEstrategia, nGrafos);
        return new ResultadoLoteDto(promedios, victorias, nGrafos);
    }

    // ── Promedio de las N corridas de una estrategia ──────────────────────────

    private ResultadoSimulacionDto promediar(List<ResultadoSimulacionDto> corridas) {
        int n = corridas.size();
        double sPico = 0, sTurno = 0, sDur = 0, sRec = 0, sVac = 0, sR0 = 0;
        for (ResultadoSimulacionDto r : corridas) {
            sPico  += r.getPicoMaximoInfectados();
            sTurno += r.getTurnoDePico();
            sDur   += r.getDuracionBrote();
            sRec   += r.getTotalRecuperados();
            sVac   += r.getTotalVacunados();
            sR0    += r.getR0Estimado();
        }
        int poblacion = corridas.get(0).getTotalPoblacion();
        EstrategiaVacunacion estrategia = corridas.get(0).getEstrategia();

        return new ResultadoSimulacionDto(
                estrategia,
                promediarCurva(corridas),
                (int) Math.round(sPico  / n),
                (int) Math.round(sTurno / n),
                (int) Math.round(sDur   / n),
                (int) Math.round(sRec   / n),
                (int) Math.round(sVac   / n),
                poblacion,
                sR0 / n);
    }

    /**
     * Promedia el conteo S/I/R/V por turno. Las corridas que terminaron antes se
     * tratan, tras su última entrada, con I=0 y S/R/V mantenidos en su último valor
     * (estado estable post-extinción).
     */
    private List<Map<String, Integer>> promediarCurva(List<ResultadoSimulacionDto> corridas) {
        int maxLen = corridas.stream()
                .mapToInt(r -> r.getHistorialPorTurno().size())
                .max().orElse(0);
        int n = corridas.size();
        String[] claves = {"S", "I", "R", "V"};

        List<Map<String, Integer>> curva = new ArrayList<>();
        for (int t = 0; t < maxLen; t++) {
            Map<String, Integer> punto = new LinkedHashMap<>();
            for (String k : claves) {
                long suma = 0;
                for (ResultadoSimulacionDto r : corridas) {
                    List<Map<String, Integer>> h = r.getHistorialPorTurno();
                    if (t < h.size()) {
                        suma += h.get(t).getOrDefault(k, 0);
                    } else if (!h.isEmpty() && !k.equals("I")) {
                        suma += h.get(h.size() - 1).getOrDefault(k, 0);
                    }
                }
                punto.put(k, (int) Math.round((double) suma / n));
            }
            curva.add(punto);
        }
        return curva;
    }

    // ── Conteo de victorias por corrida ───────────────────────────────────────

    private Map<EstrategiaVacunacion, Integer> contarVictorias(
            List<List<ResultadoSimulacionDto>> porEstrategia, int nGrafos) {
        Map<EstrategiaVacunacion, Integer> victorias = new LinkedHashMap<>();
        for (List<ResultadoSimulacionDto> corridas : porEstrategia) {
            if (!corridas.isEmpty()) victorias.put(corridas.get(0).getEstrategia(), 0);
        }

        for (int i = 0; i < nGrafos; i++) {
            List<ResultadoSimulacionDto> ronda = new ArrayList<>();
            for (List<ResultadoSimulacionDto> corridas : porEstrategia) {
                if (i < corridas.size()) ronda.add(corridas.get(i));
            }
            if (ronda.isEmpty()) continue;
            List<AnalisisComparativo.ScoreEstrategia> ranking = analisis.calcularRanking(ronda);
            if (!ranking.isEmpty()) {
                victorias.merge(ranking.get(0).estrategia, 1, Integer::sum);
            }
        }
        return victorias;
    }
}
