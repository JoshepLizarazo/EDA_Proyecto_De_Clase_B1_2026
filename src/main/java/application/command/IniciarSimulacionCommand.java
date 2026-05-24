package application.command;

import application.dto.ConfiguracionDto;
import application.dto.ResultadoLoteDto;
import application.dto.ResultadoSimulacionDto;
import application.service.SimulacionService;
import application.service.VacunacionService;
import domain.model.RedSocial;
import domain.value.EstrategiaVacunacion;
import infrastructure.persistence.CargadorRedCSV;
import infrastructure.util.AgregadorLote;
import infrastructure.util.GeneradorPoblacion;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.JComponent;
import presentation.GraficoSimulacion;
import presentation.VentanaComparativaTabs;

/**
 * Orquesta el flujo completo de una simulación:
 *   1. Construir la red (generar o cargar CSV)
 *   2. Vacunar el 20%
 *   3. Infectar paciente cero
 *   4. Correr la simulación
 */
public class IniciarSimulacionCommand {

    private final GeneradorPoblacion generador;
    private final CargadorRedCSV cargador;
    private final VacunacionService vacunacionService;
    private final SimulacionService simulacionService;
    private final AplicarVacunacionCommand vacunacionCommand;

    public IniciarSimulacionCommand(long semilla) {
        this.generador          = new GeneradorPoblacion();
        this.cargador           = new CargadorRedCSV();
        this.vacunacionService  = new VacunacionService(semilla);
        this.simulacionService  = new SimulacionService();
        this.vacunacionCommand  = new AplicarVacunacionCommand(vacunacionService);
    }

    /**
     * Ejecuta la simulación con la estrategia indicada en {@code config}.
     */
    public ResultadoSimulacionDto ejecutar(ConfiguracionDto config) {
        return ejecutar(config, null);
    }

    public ResultadoSimulacionDto ejecutar(ConfiguracionDto config, GraficoSimulacion grafico) {
        RedSocial red = construirRed(config);
        if (grafico != null) {
            grafico.inicializar(red);
        }
        // Paciente cero ANTES de vacunar: las 6 estrategias arrancan con los mismos
        // infectados (misma red + misma semilla) y nunca se vacuna a un nodo ya infectado.
        red.setearPacienteCero(config.getCantidadPacientesCero(), new Random(config.getSemillaAleatoria() + 1));
        vacunacionCommand.ejecutar(red, config.getEstrategia());
        return simulacionService.ejecutar(red, config, grafico);
    }

    /**
     * Ejecuta las 6 estrategias sobre redes independientes y retorna todos los resultados.
     * Cada estrategia parte de la misma semilla para resultados comparables.
     *
     * Si {@code config.isMostrarVisualizacion()} es true, abre una sola ventana con
     * un JTabbedPane y va poblando una pestaña por estrategia secuencialmente.
     * En modo headless o sin visualización, ejecuta el flujo de consola tradicional.
     */
    public List<ResultadoSimulacionDto> ejecutarComparativo(ConfiguracionDto config) {
        List<ResultadoSimulacionDto> resultados = new ArrayList<>();
        VentanaComparativaTabs ventana = null;
        if (config.isMostrarVisualizacion()) {
            ventana = new VentanaComparativaTabs();
            if (!ventana.isDisponible()) ventana = null;
        }

        for (EstrategiaVacunacion e : EstrategiaVacunacion.values()) {
            ConfiguracionDto cfg = clonar(config, e);
            System.out.printf("%n══════ Estrategia: %-14s ══════%n", e);

            if (ventana != null) {
                resultados.add(ejecutarConTab(cfg, ventana));
            } else {
                resultados.add(ejecutar(cfg, null));
            }
        }
        return resultados;
    }

    // ── Experimento por lotes ──────────────────────────────────────────────────

    /** Callback de avance del lote (corrida completadas / total) para refrescar la UI. */
    @FunctionalInterface
    public interface ProgresoLote {
        void avance(int completadas, int total, String detalle);
    }

    /**
     * Corre {@code nGrafos} grafos DISTINTOS por cada una de las 6 estrategias
     * (6 × nGrafos simulaciones en total; ningún grafo se comparte) y agrega los
     * resultados: promedia las métricas de cada estrategia y cuenta en cuántas
     * corridas cada una obtuvo el mejor score compuesto.
     *
     * Cada corrida usa una semilla única {@code base + s*nGrafos + i}, por lo que
     * x100 produce 600 grafos independientes. Sin visualización: es cómputo puro.
     */
    public ResultadoLoteDto ejecutarLote(ConfiguracionDto config, int nGrafos, ProgresoLote progreso) {
        EstrategiaVacunacion[] estrategias = EstrategiaVacunacion.values();
        int total  = estrategias.length * nGrafos;
        int hechas = 0;
        long base  = config.getSemillaAleatoria();

        List<List<ResultadoSimulacionDto>> porEstrategia = new ArrayList<>();
        for (int s = 0; s < estrategias.length; s++) {
            List<ResultadoSimulacionDto> deEstrategia = new ArrayList<>();
            for (int i = 0; i < nGrafos; i++) {
                long seed = base + (long) s * nGrafos + i;   // único por (estrategia, corrida)

                ConfiguracionDto cfg = clonar(config, estrategias[s]);
                cfg.setSemillaAleatoria(seed);
                cfg.setMostrarVisualizacion(false);

                // Command propio por corrida: generación de red, vacunación aleatoria
                // y paciente cero quedan ligados a la semilla de este grafo concreto.
                IniciarSimulacionCommand cmd = new IniciarSimulacionCommand(seed);
                deEstrategia.add(cmd.ejecutar(cfg, null));

                hechas++;
                if (progreso != null) {
                    progreso.avance(hechas, total,
                            String.format("[%s]  grafo %d / %d", estrategias[s], i + 1, nGrafos));
                }
            }
            porEstrategia.add(deEstrategia);
        }
        return new AgregadorLote().agregar(porEstrategia, nGrafos);
    }

    // ── Ejecución con vista embebida en una pestaña ───────────────────────────

    private ResultadoSimulacionDto ejecutarConTab(ConfiguracionDto config,
                                                  VentanaComparativaTabs ventana) {
        RedSocial red = construirRed(config);

        GraficoSimulacion grafico = new GraficoSimulacion();
        JComponent vista = grafico.inicializarEmbebido(red);
        if (vista != null) {
            ventana.agregarTab(config.getEstrategia().name(), vista);
        }

        // Paciente cero ANTES de vacunar (ver ejecutar): comparación justa entre estrategias.
        red.setearPacienteCero(config.getCantidadPacientesCero(),
                new Random(config.getSemillaAleatoria() + 1));
        vacunacionCommand.ejecutar(red, config.getEstrategia());
        return simulacionService.ejecutar(red, config, grafico);
    }

    // ── Auxiliares ────────────────────────────────────────────────────────────

    private RedSocial construirRed(ConfiguracionDto config) {
        if (config.isUsarCSV()) {
            try {
                System.out.println("  Cargando red desde CSV...");
                return cargador.cargar(config.getArchivoPersonas(), config.getArchivoContactos());
            } catch (IOException ex) {
                System.out.println("  CSV no encontrado, generando red aleatoria: " + ex.getMessage());
            }
        }
        System.out.printf("  Generando red de %d personas...%n", config.getTamanoRed());
        return generador.generar(config.getTamanoRed(), config.getSemillaAleatoria());
    }

    /** Clona la config conservando todo menos la estrategia (y forzando visualización en su origen). */
    private ConfiguracionDto clonar(ConfiguracionDto orig, EstrategiaVacunacion e) {
        ConfiguracionDto cfg = new ConfiguracionDto();
        cfg.setTamanoRed(orig.getTamanoRed());
        cfg.setTurnosMaximos(orig.getTurnosMaximos());
        cfg.setSemillaAleatoria(orig.getSemillaAleatoria());
        cfg.setProbInfeccionBase(orig.getProbInfeccionBase());
        cfg.setDiasRecuperacion(orig.getDiasRecuperacion());
        cfg.setCantidadPacientesCero(orig.getCantidadPacientesCero());
        cfg.setUsarCSV(orig.isUsarCSV());
        cfg.setArchivoPersonas(orig.getArchivoPersonas());
        cfg.setArchivoContactos(orig.getArchivoContactos());
        cfg.setMostrarVisualizacion(orig.isMostrarVisualizacion());
        cfg.setPausaVisualizacionMs(orig.getPausaVisualizacionMs());
        cfg.setEstrategia(e);
        return cfg;
    }
}
