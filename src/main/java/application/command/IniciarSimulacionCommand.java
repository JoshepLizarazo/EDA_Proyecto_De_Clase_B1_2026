package application.command;

import application.dto.ConfiguracionDto;
import application.dto.ResultadoSimulacionDto;
import application.service.SimulacionService;
import application.service.VacunacionService;
import domain.model.RedSocial;
import domain.value.EstrategiaVacunacion;
import infrastructure.persistence.CargadorRedCSV;
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
        vacunacionCommand.ejecutar(red, config.getEstrategia());
        red.setearPacienteCero(config.getCantidadPacientesCero(), new Random(config.getSemillaAleatoria() + 1));
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

    // ── Ejecución con vista embebida en una pestaña ───────────────────────────

    private ResultadoSimulacionDto ejecutarConTab(ConfiguracionDto config,
                                                  VentanaComparativaTabs ventana) {
        RedSocial red = construirRed(config);

        GraficoSimulacion grafico = new GraficoSimulacion();
        JComponent vista = grafico.inicializarEmbebido(red);
        if (vista != null) {
            ventana.agregarTab(config.getEstrategia().name(), vista);
        }

        vacunacionCommand.ejecutar(red, config.getEstrategia());
        red.setearPacienteCero(config.getCantidadPacientesCero(),
                new Random(config.getSemillaAleatoria() + 1));
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
