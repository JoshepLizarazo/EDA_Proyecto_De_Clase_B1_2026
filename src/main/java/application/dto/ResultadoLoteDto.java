package application.dto;

import domain.value.EstrategiaVacunacion;
import java.util.List;
import java.util.Map;

/**
 * Resultado de un experimento por lotes.
 *
 * Cada estrategia se ejecuta sobre {@code nGrafosPorEstrategia} grafos DISTINTOS
 * e independientes (ningún grafo se comparte entre estrategias). Este DTO reúne:
 *
 *   - {@code promedios}: un {@link ResultadoSimulacionDto} por estrategia con las
 *     métricas promediadas sobre sus N corridas (y una curva I(t) promedio).
 *   - {@code victorias}: en cuántas de las N corridas cada estrategia obtuvo el
 *     mejor score compuesto (lectura "acumulada" de la comparación).
 */
public class ResultadoLoteDto {

    private final List<ResultadoSimulacionDto> promedios;
    private final Map<EstrategiaVacunacion, Integer> victorias;
    private final int nGrafosPorEstrategia;

    public ResultadoLoteDto(List<ResultadoSimulacionDto> promedios,
                            Map<EstrategiaVacunacion, Integer> victorias,
                            int nGrafosPorEstrategia) {
        this.promedios            = promedios;
        this.victorias            = victorias;
        this.nGrafosPorEstrategia = nGrafosPorEstrategia;
    }

    public List<ResultadoSimulacionDto> getPromedios()       { return promedios; }
    public Map<EstrategiaVacunacion, Integer> getVictorias() { return victorias; }
    public int getNGrafosPorEstrategia()                     { return nGrafosPorEstrategia; }

    /** Total de simulaciones ejecutadas en el lote: estrategias × grafos por estrategia. */
    public int getTotalSimulaciones() {
        return promedios.size() * nGrafosPorEstrategia;
    }
}
