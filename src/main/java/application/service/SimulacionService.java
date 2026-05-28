package application.service;

import application.dto.ConfiguracionDto;
import application.dto.ResultadoSimulacionDto;
import domain.algoritmo.AjustadorPesosAdaptativo;
import domain.algoritmo.AjustadorPesosAdaptativo.CambioFatiga;
import domain.algoritmo.GestorEventos;
import domain.algoritmo.ModeloSIRV;
import domain.model.Contacto;
import domain.model.EventoEpidemiologico;
import domain.model.Persona;
import domain.model.RedSocial;
import domain.value.EstadoSIRV;
import infrastructure.util.CalculadorEstadisticas;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import presentation.GraficoSimulacion;

/**
 * Orquesta el loop de turnos de una simulación.
 *
 * Por turno:
 *   1. ModeloSIRV.simularTurno()           — propaga la infección con los pesos actuales
 *   2. GestorEventos.evaluar()             — marca eventos NPI si se supera un umbral
 *   3. Registrar conteo {S,I,R,V}          — añadir al historial
 *   4. AjustadorPesosAdaptativo.ajustar()  — recompone pesos para el siguiente turno:
 *                                            base × factorEventos × vigilancia × fatiga
 *   5. GraficoSimulacion.actualizarTurno() — actualiza colores en pantalla (opcional)
 *   6. Verificar parada: sin infectados o turnos agotados
 */
public class SimulacionService {

    private final CalculadorEstadisticas calculador;

    public SimulacionService() {
        this.calculador = new CalculadorEstadisticas();
    }

    /**
     * Ejecuta la simulación completa y retorna el resultado con métricas.
     *
     * @param red    red social ya inicializada (con vacunados y paciente cero)
     * @param config configuración de la simulación
     * @param grafico visualizador GraphStream (puede ser null si no se usa UI)
     */
    public ResultadoSimulacionDto ejecutar(RedSocial red, ConfiguracionDto config,
                                           GraficoSimulacion grafico) {
        ModeloSIRV modelo = new ModeloSIRV(config.getDiasRecuperacion(), config.getSemillaAleatoria());
        GestorEventos gestor = new GestorEventos();
        AjustadorPesosAdaptativo ajustador = new AjustadorPesosAdaptativo();
        List<Map<String, Integer>> historial = new ArrayList<>();
        boolean capturarPesos = config.isCapturarHistorialPesos();
        List<Map<String, Double>> historialPesos = new ArrayList<>();

        // Registrar estado inicial (turno 0)
        historial.add(contarEstados(red));
        // Ajuste previo al primer turno: aplica vigilancia local del paciente cero
        ajustador.ajustar(red, gestor.getFactorAcumuladoEventos(), historial);
        if (capturarPesos) historialPesos.add(snapshotPesos(red));

        System.out.printf("%nIniciando simulación — %s%n", config);
        System.out.printf("%-6s | %4s | %4s | %4s | %4s%n", "Turno", "S", "I", "R", "V");
        System.out.println("─".repeat(30));
        imprimirTurno(0, historial.get(0));

        for (int t = 1; t <= config.getTurnosMaximos(); t++) {
            Map<String, Integer> conteo = modelo.simularTurno(red);
            List<EventoEpidemiologico> disparados = gestor.evaluar(red, t);
            historial.add(conteo);
            CambioFatiga cambioFatiga = ajustador.ajustar(red, gestor.getFactorAcumuladoEventos(), historial);
            if (capturarPesos) historialPesos.add(snapshotPesos(red));
            imprimirTurno(t, conteo);

            if (grafico != null) {
                // Alertas por eventos NPI
                for (EventoEpidemiologico ev : disparados) {
                    grafico.alertaEventoNPI(ev.getNombre());
                }
                // Alerta por cambio de fatiga social
                if (cambioFatiga == CambioFatiga.INICIADA) {
                    grafico.alertaFatigaIniciada();
                } else if (cambioFatiga == CambioFatiga.REINICIADA) {
                    grafico.alertaFatigaReiniciada();
                }
                grafico.actualizarTurno(red, t);
                pausar(config.getPausaVisualizacionMs());
            }

            // Condición de parada: sin infectados Y sin susceptibles (todos curados/vacunados)
            if (conteo.getOrDefault("I", 0) == 0 && conteo.getOrDefault("S", 0) == 0) {
                System.out.printf("  → Todos recuperados/vacunados en el turno %d%n", t);
                break;
            }
            // Parada secundaria: sin infectados pero quedan susceptibles (brote contenido)
            if (conteo.getOrDefault("I", 0) == 0) {
                System.out.printf("  → Brote extinguido en el turno %d (%d susceptibles no alcanzados)%n",
                        t, conteo.getOrDefault("S", 0));
                break;
            }
        }

        ResultadoSimulacionDto resultado =
                calculador.construirResultado(config.getEstrategia(), historial, red.getTotalPersonas());
        if (capturarPesos) resultado.setHistorialPesosPorTurno(historialPesos);
        return resultado;
    }

    /**
     * Toma una "foto" del peso de cada nodo en el turno actual: el promedio de la
     * probContagio de sus aristas salientes (probabilidad efectiva de contagiar a un vecino).
     * Un nodo sin aristas salientes registra 0.0.
     */
    private Map<String, Double> snapshotPesos(RedSocial red) {
        Map<String, Double> snapshot = new LinkedHashMap<>();
        for (Persona p : red.getTodasLasPersonas()) {
            List<Contacto> salientes = red.getContactos(p);
            double promedio = 0.0;
            if (!salientes.isEmpty()) {
                double suma = 0.0;
                for (Contacto c : salientes) suma += c.getProbContagio();
                promedio = suma / salientes.size();
            }
            snapshot.put(p.getId(), promedio);
        }
        return snapshot;
    }

    // ── Auxiliares ────────────────────────────────────────────────────────────

    private Map<String, Integer> contarEstados(RedSocial red) {
        java.util.LinkedHashMap<String, Integer> m = new java.util.LinkedHashMap<>();
        m.put("S", red.getPersonasPorEstado(EstadoSIRV.SUSCEPTIBLE).size());
        m.put("I", red.getPersonasPorEstado(EstadoSIRV.INFECTADO).size());
        m.put("R", red.getPersonasPorEstado(EstadoSIRV.RECUPERADO).size());
        m.put("V", red.getPersonasPorEstado(EstadoSIRV.VACUNADO).size());
        return m;
    }

    private void imprimirTurno(int t, Map<String, Integer> conteo) {
        System.out.printf("%-6d | %4d | %4d | %4d | %4d%n",
                t,
                conteo.getOrDefault("S", 0),
                conteo.getOrDefault("I", 0),
                conteo.getOrDefault("R", 0),
                conteo.getOrDefault("V", 0));
    }

    private void pausar(int ms) {
        if (ms <= 0) return;
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
