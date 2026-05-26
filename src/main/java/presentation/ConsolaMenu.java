package presentation;

import application.command.IniciarSimulacionCommand;
import application.dto.ConfiguracionDto;
import application.dto.ResultadoSimulacionDto;
import domain.value.EstrategiaVacunacion;
import infrastructure.persistence.ExportadorResultados;
import infrastructure.persistence.GeneradorReportePDF;
import infrastructure.util.AnalisisComparativo;
import infrastructure.util.AnalisisComparativo.ScoreEstrategia;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;

/**
 * Interacción con el usuario por consola. Construye la ConfiguracionDto
 * y delega la ejecución a IniciarSimulacionCommand.
 *
 * Flujo de visualización y exportación:
 *   - Modo individual: GraphStream abre su propia ventana. NO se ofrece PDF
 *     (no hay nada que comparar con una sola estrategia).
 *   - Modo comparativo: si se elige visualización, abre UNA ventana con 6 pestañas
 *     (una por estrategia) y al final ofrece exportación TXT/PDF con la
 *     comparativa cuantitativa de las 6 estrategias.
 */
public class ConsolaMenu {

    private final Scanner sc = new Scanner(System.in);
    private final PanelEstadisticas panel = new PanelEstadisticas();
    private final ExportadorResultados exportador = new ExportadorResultados();
    private final GeneradorReportePDF generadorPdf = new GeneradorReportePDF();
    private final AnalisisComparativo analisis = new AnalisisComparativo();

    /** Bucle principal del menú. */
    public void iniciar() {
        imprimirBanner();
        boolean salir = false;
        while (!salir) {
            System.out.println("\n╔════════════════════════════════╗");
            System.out.println("║         MENÚ PRINCIPAL         ║");
            System.out.println("╠════════════════════════════════╣");
            System.out.println("║ 1. Simulación individual       ║");
            System.out.println("║ 2. Comparativo 6 estrategias   ║");
            System.out.println("║ 3. Construcción visual red     ║");
            System.out.println("║ 4. Salir                       ║");
            System.out.println("╚════════════════════════════════╝");
            System.out.print("Opción: ");

            switch (leerInt()) {
                case 1 -> ejecutarIndividual();
                case 2 -> ejecutarComparativo();
                case 3 -> ejecutarConstruccionVisual();
                case 4 -> salir = true;
                case -1 -> salir = true; // EOF (piped stdin or IDE close)
                default -> System.out.println("  Opción inválida.");
            }
        }
        System.out.println("\n¡Hasta luego!");
    }

    // ── Flujos de ejecución ───────────────────────────────────────────────────

    private void ejecutarIndividual() {
        ConfiguracionDto config = solicitarConfiguracion(true);
        IniciarSimulacionCommand cmd = new IniciarSimulacionCommand(config.getSemillaAleatoria());

        GraficoSimulacion grafico = null;
        if (config.isMostrarVisualizacion()) {
            grafico = new GraficoSimulacion();
        }

        ResultadoSimulacionDto resultado = cmd.ejecutar(config, grafico);
        List<ResultadoSimulacionDto> lista = List.of(resultado);
        panel.mostrar(lista);
        ofrecerExportacionTexto(lista);
    }

    private void ejecutarComparativo() {
        ConfiguracionDto config = solicitarConfiguracion(false);
        IniciarSimulacionCommand cmd = new IniciarSimulacionCommand(config.getSemillaAleatoria());

        System.out.println("\nEjecutando 6 estrategias, por favor espere...");
        List<ResultadoSimulacionDto> resultados = cmd.ejecutarComparativo(config);
        panel.mostrar(resultados);
        mostrarAnalisisGanador(resultados);
        ofrecerExportacionCompleta(resultados);
    }

    // ── Análisis del ganador ──────────────────────────────────────────────────

    private void mostrarAnalisisGanador(List<ResultadoSimulacionDto> resultados) {
        List<ScoreEstrategia> ranking = analisis.calcularRanking(resultados);
        if (ranking.isEmpty()) return;

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║           ANÁLISIS CUANTITATIVO — SCORE COMPUESTO                    ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");
        System.out.printf( "║ %-14s│ %-7s│ %-44s ║%n", "Estrategia", "Score", "Componentes (pico/dur/afect/cont/R0)");
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");

        for (int i = 0; i < ranking.size(); i++) {
            ScoreEstrategia s = ranking.get(i);
            String marca = (i == 0) ? "★" : " ";
            System.out.printf("║%s%-14s│ %.3f │ %.2f / %.2f / %.2f / %.2f / %.2f%14s ║%n",
                marca, s.estrategia, s.score,
                s.componentes.getOrDefault("Pico", 0.0),
                s.componentes.getOrDefault("Duración", 0.0),
                s.componentes.getOrDefault("Afectados", 0.0),
                s.componentes.getOrDefault("Contención", 0.0),
                s.componentes.getOrDefault("R0", 0.0),
                "");
        }
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println(analisis.justificarGanador(ranking, resultados));
    }

    // ── Solicitud de configuración al usuario ─────────────────────────────────

    private ConfiguracionDto solicitarConfiguracion(boolean pedirEstrategia) {
        ConfiguracionDto cfg = new ConfiguracionDto();

        System.out.println("\n── Configuración de la simulación ──");

        System.out.println("Tamaño de red:");
        System.out.println("  1. Red pequeña (~80 nodos)");
        System.out.println("  2. Red grande (~300 nodos)");
        System.out.println("  3. Personalizado");
        System.out.print("Opción [1]: ");
        switch (leerIntDefault(1)) {
            case 2 -> cfg.setTamanoRed(300);
            case 3 -> { System.out.print("N personas: "); cfg.setTamanoRed(Math.max(10, leerInt())); }
            default -> cfg.setTamanoRed(80);
        }

        if (pedirEstrategia) {
            System.out.println("\nEstrategia de vacunación:");
            System.out.println("  1. Aleatoria   2. Hubs   3. Betweenness");
            System.out.println("  4. Comunidades 5. Híbrida   6. BFS Ponderado");
            System.out.print("Opción [1]: ");
            EstrategiaVacunacion[] estrategias = EstrategiaVacunacion.values();
            int idx = Math.max(0, Math.min(estrategias.length - 1, leerIntDefault(1) - 1));
            cfg.setEstrategia(estrategias[idx]);
        } else {
            System.out.println("\n  (Modo comparativo: se ejecutarán las 6 estrategias automáticamente)");
        }

        System.out.print("\nTurnos máximos [60]: ");
        cfg.setTurnosMaximos(Math.max(1, leerIntDefault(60)));

        System.out.print("Días de recuperación [7]: ");
        cfg.setDiasRecuperacion(Math.max(1, leerIntDefault(7)));

        System.out.print("¿Mostrar visualización GraphStream? (s/N) [N]: ");
        String vis = sc.nextLine().trim().toLowerCase();
        cfg.setMostrarVisualizacion(vis.equals("s") || vis.equals("si") || vis.equals("sí"));

        System.out.print("¿Cargar desde CSV? (s/N) [N]: ");
        String csv = sc.nextLine().trim().toLowerCase();
        cfg.setUsarCSV(csv.equals("s") || csv.equals("si") || csv.equals("sí"));

        System.out.println("\n" + cfg);
        return cfg;
    }

    // ── Exportación ───────────────────────────────────────────────────────────

    /** Modo individual: solo TXT, sin PDF (no hay nada que comparar). */
    private void ofrecerExportacionTexto(List<ResultadoSimulacionDto> resultados) {
        System.out.print("\n¿Exportar resultado a archivo de texto? (s/N): ");
        String resp = sc.nextLine().trim().toLowerCase();
        if (resp.equals("s") || resp.equals("si") || resp.equals("sí")) {
            String ruta = "resultados_simulacion.txt";
            try {
                exportador.exportar(resultados, ruta);
                System.out.println("  Texto guardado en: " + ruta);
            } catch (IOException e) {
                System.out.println("  Error al exportar texto: " + e.getMessage());
            }
        }
    }

    /** Modo comparativo: TXT y/o PDF (PDF tiene sentido solo aquí). */
    private void ofrecerExportacionCompleta(List<ResultadoSimulacionDto> resultados) {
        System.out.println("\n¿Exportar resultados?");
        System.out.println("  1. Archivo de texto (.txt)");
        System.out.println("  2. Reporte PDF con gráficos comparativos");
        System.out.println("  3. Ambos");
        System.out.println("  Cualquier otra tecla: omitir");
        System.out.print("Opción: ");

        String resp = sc.nextLine().trim();
        boolean txt = resp.equals("1") || resp.equals("3");
        boolean pdf = resp.equals("2") || resp.equals("3");

        if (txt) {
            String ruta = "resultados_simulacion.txt";
            try {
                exportador.exportar(resultados, ruta);
                System.out.println("  Texto guardado en: " + ruta);
            } catch (IOException e) {
                System.out.println("  Error al exportar texto: " + e.getMessage());
            }
        }
        if (pdf) {
            String ruta = "reporte_simulacion.pdf";
            try {
                generadorPdf.exportar(resultados, ruta);
                System.out.println("  PDF guardado en: " + ruta);
            } catch (IOException e) {
                System.out.println("  Error al exportar PDF: " + e.getMessage());
            }
        }
    }

    // ── Lectura de input ──────────────────────────────────────────────────────

    private int leerInt() {
        if (!sc.hasNextLine()) return -1; // EOF → exit the menu loop
        try { return Integer.parseInt(sc.nextLine().trim()); }
        catch (Exception e) { return 0; }
    }

    private int leerIntDefault(int def) {
        try {
            String s = sc.nextLine().trim();
            return s.isEmpty() ? def : Integer.parseInt(s);
        } catch (Exception e) { return def; }
    }

    private void ejecutarConstruccionVisual() {
        System.out.println("\n── Construcción Visual de la Red ──");
        System.out.println("Tamaño de red (para ver el paso a paso conviene poca):");
        System.out.println("  1. Demo (~25 nodos)");
        System.out.println("  2. Pequeña (~80 nodos)");
        System.out.println("  3. Personalizado");
        System.out.print("Opción [1]: ");
        int tamano;
        switch (leerIntDefault(1)) {
            case 2 -> tamano = 80;
            case 3 -> { System.out.print("N personas: "); tamano = Math.max(10, leerInt()); }
            default -> tamano = 25;
        }
        System.out.print("Pausa entre fases en ms [600]: ");
        int pausa = Math.max(200, leerIntDefault(600));

        long semilla = new java.util.Random().nextLong();
        System.out.println("\nAbriendo ventana de construcción visual...");
        new VisualizadorConstruccionRed().visualizar(tamano, semilla, pausa);
        System.out.println("  (ventana cerrada)");
    }

    // ── Auxiliares ────────────────────────────────────────────────────────────

    private void imprimirBanner() {
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║    SIMULADOR DE PROPAGACIÓN EPIDÉMICA — SIRV         ║");
        System.out.println("║    Universidad Industrial de Santander — UIS         ║");
        System.out.println("║    Estructuras de Datos y Análisis de Algoritmos     ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
    }
}
