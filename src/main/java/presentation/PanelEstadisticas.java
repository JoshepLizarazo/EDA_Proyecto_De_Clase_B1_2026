package presentation;

import application.dto.ResultadoSimulacionDto;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Muestra el resumen comparativo de todas las estrategias en consola
 * con una tabla y un gráfico ASCII de curvas de infectados por turno.
 */
public class PanelEstadisticas {

    /**
     * Imprime la tabla comparativa y el gráfico ASCII de curvas I(t).
     */
    public void mostrar(List<ResultadoSimulacionDto> resultados) {
        if (resultados == null || resultados.isEmpty()) {
            System.out.println("Sin resultados para mostrar.");
            return;
        }

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║          COMPARATIVO FINAL — ESTRATEGIAS DE VACUNACIÓN              ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");
        System.out.printf("║ %-14s│%6s│%7s│%9s│%13s│%12s│%6s ║%n",
                "Estrategia", "Pico", "t-Pico", "Duración", "Recuperados", "Contención%", "R0");
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");

        ResultadoSimulacionDto ganador = resultados.stream()
                .min(Comparator.comparingInt(ResultadoSimulacionDto::getPicoMaximoInfectados))
                .orElse(null);

        for (ResultadoSimulacionDto r : resultados) {
            String marca = r.equals(ganador) ? "★" : " ";
            System.out.printf("║%s%-14s│%6d│%7d│%9d│%13d│%11.1f%%│%6.2f ║%n",
                    marca,
                    r.getEstrategia(),
                    r.getPicoMaximoInfectados(),
                    r.getTurnoDePico(),
                    r.getDuracionBrote(),
                    r.getTotalRecuperados(),
                    r.getPorcentajeContencion(),
                    r.getR0Estimado());
        }

        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");
        if (ganador != null) {
            System.out.printf("║ ★ GANADOR: %-14s — Pico: %3d | Contención: %.1f%%%20s║%n",
                    ganador.getEstrategia(),
                    ganador.getPicoMaximoInfectados(),
                    ganador.getPorcentajeContencion(), "");
        }
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");

        // Gráfico ASCII de curvas I(t)
        System.out.println();
        graficoAscii(resultados);
    }

    // ── Gráfico ASCII ─────────────────────────────────────────────────────────

    private void graficoAscii(List<ResultadoSimulacionDto> resultados) {
        int alturaGrafico = 15;
        int anchura = 60;

        // Encontrar el máximo global de infectados para normalizar
        int maxGlobal = resultados.stream()
                .mapToInt(ResultadoSimulacionDto::getPicoMaximoInfectados)
                .max().orElse(1);

        int maxTurnos = resultados.stream()
                .mapToInt(r -> r.getHistorialPorTurno().size())
                .max().orElse(1);

        System.out.println("  Curva de Infectados I(t) por estrategia:");
        System.out.println("  " + "─".repeat(anchura + 4));

        char[] simbolos = {'▪', '○', '◆', '△', '●', '◇'};
        String[] nombres = resultados.stream()
                .map(r -> r.getEstrategia().name())
                .toArray(String[]::new);

        // Construir la grilla
        char[][] grid = new char[alturaGrafico][anchura];
        for (char[] fila : grid) java.util.Arrays.fill(fila, ' ');

        for (int ri = 0; ri < resultados.size(); ri++) {
            ResultadoSimulacionDto r = resultados.get(ri);
            List<Map<String, Integer>> hist = r.getHistorialPorTurno();
            char sym = simbolos[ri % simbolos.length];
            for (int t = 0; t < hist.size(); t++) {
                int x = (int) ((double) t / maxTurnos * (anchura - 1));
                int infectados = hist.get(t).getOrDefault("I", 0);
                int y = (int) ((double) infectados / maxGlobal * (alturaGrafico - 1));
                y = alturaGrafico - 1 - y;
                if (x < anchura && y >= 0 && y < alturaGrafico) grid[y][x] = sym;
            }
        }

        // Imprimir grilla
        for (int y = 0; y < alturaGrafico; y++) {
            int valorY = (int) ((alturaGrafico - 1 - y) * maxGlobal / (double)(alturaGrafico - 1));
            System.out.printf("  %4d│%s│%n", valorY, new String(grid[y]));
        }
        System.out.println("      └" + "─".repeat(anchura) + "→ turnos");

        // Leyenda
        System.out.println();
        for (int i = 0; i < resultados.size(); i++) {
            System.out.printf("  %c = %s%n", simbolos[i % simbolos.length], nombres[i]);
        }
    }
}
