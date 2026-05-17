package infrastructure.persistence;

import application.dto.ResultadoSimulacionDto;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Exporta el comparativo final de las estrategias a un archivo de texto.
 */
public class ExportadorResultados {

    /**
     * Escribe los resultados en {@code rutaArchivo} en formato tabla de texto.
     */
    public void exportar(List<ResultadoSimulacionDto> resultados, String rutaArchivo) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(rutaArchivo))) {
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            pw.println("═══════════════════════════════════════════════════════════════");
            pw.println("  SIMULADOR DE PROPAGACIÓN EPIDÉMICA — Resultados Comparativos");
            pw.println("  Generado: " + timestamp);
            pw.println("═══════════════════════════════════════════════════════════════");
            pw.println();

            if (resultados.isEmpty()) {
                pw.println("Sin resultados.");
                return;
            }

            // Cabecera tabla
            pw.printf("%-14s | %5s | %6s | %8s | %12s | %11s | %6s%n",
                    "Estrategia", "Pico", "t-Pico", "Duración", "Recuperados", "Contención%", "R0");
            pw.println("─".repeat(75));

            for (ResultadoSimulacionDto r : resultados) {
                pw.printf("%-14s | %5d | %6d | %8d | %12d | %10.1f%% | %6.2f%n",
                        r.getEstrategia(),
                        r.getPicoMaximoInfectados(),
                        r.getTurnoDePico(),
                        r.getDuracionBrote(),
                        r.getTotalRecuperados(),
                        r.getPorcentajeContencion(),
                        r.getR0Estimado());
            }

            pw.println("─".repeat(75));

            // Ganador: menor pico máximo de infectados
            ResultadoSimulacionDto ganador = resultados.stream()
                    .min(Comparator.comparingInt(ResultadoSimulacionDto::getPicoMaximoInfectados))
                    .orElse(null);
            if (ganador != null) {
                pw.println();
                pw.printf("  ★ ESTRATEGIA GANADORA: %s%n", ganador.getEstrategia());
                pw.printf("    Pico: %d infectados | Contención: %.1f%% | R0≈%.2f%n",
                        ganador.getPicoMaximoInfectados(),
                        ganador.getPorcentajeContencion(),
                        ganador.getR0Estimado());
            }

            pw.println();
            pw.println("═══════════════════════════════════════════════════════════════");
        }
    }
}
