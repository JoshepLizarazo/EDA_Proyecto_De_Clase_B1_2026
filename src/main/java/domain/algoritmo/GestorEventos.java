package domain.algoritmo;

import domain.model.EventoEpidemiologico;
import domain.model.RedSocial;
import domain.value.EstadoSIRV;
import domain.value.TipoEvento;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Modela intervenciones no farmacéuticas (NPI) que modifican la red durante la simulación.
 *
 * Responsabilidad en v11:
 *   GestorEventos ya NO modifica los pesos de las aristas directamente. En cambio,
 *   rastrea el producto acumulado de los factores de todos los eventos disparados
 *   ({@code factorAcumuladoEventos}) y lo expone via {@code getFactorAcumuladoEventos()}.
 *   {@code AjustadorPesosAdaptativo} usa ese valor junto con la vigilancia local y la
 *   fatiga social para recomponer el peso efectivo de cada arista en cada turno.
 *
 * Tres eventos predefinidos (en orden creciente de umbral):
 *
 *   ALERTA_LEVE (umbral 30%, factor 0.80):
 *     Recomendaciones básicas de bioseguridad. Reduce contagio un 20%.
 *
 *   CUARENTENA (umbral 50%, factor 0.50):
 *     Medidas de distanciamiento social, teletrabajo, cierres parciales.
 *
 *   LOCKDOWN (umbral 70%, factor 0.20):
 *     Confinamiento estricto.
 *
 *   Factores acumulativos: si se disparan los tres,
 *     factorAcumuladoEventos = 0.80 × 0.50 × 0.20 = 0.08
 *
 * Cada evento se dispara exactamente UNA VEZ por simulación (flag estaDisparado()).
 *
 * @see AjustadorPesosAdaptativo que aplica factorAcumuladoEventos junto a vigilancia y fatiga
 * @see ModeloSIRV que usa las probContagio efectivas en cada turno
 */
public class GestorEventos {

    private final List<EventoEpidemiologico> eventos;
    private double factorAcumuladoEventos = 1.0;

    public GestorEventos() {
        eventos = new ArrayList<>();
        // Umbrales y factores calibrados para reflejar respuestas NPI colombianas
        eventos.add(new EventoEpidemiologico(TipoEvento.ALERTA_LEVE, 0.30, 0.80, "Alerta Leve"));
        eventos.add(new EventoEpidemiologico(TipoEvento.CUARENTENA,  0.50, 0.50, "Cuarentena"));
        eventos.add(new EventoEpidemiologico(TipoEvento.LOCKDOWN,    0.70, 0.20, "Lockdown"));
    }

    /**
     * Evalúa si algún umbral fue superado y marca los eventos correspondientes.
     * NO modifica las aristas directamente: actualiza {@code factorAcumuladoEventos}
     * y deja que {@code AjustadorPesosAdaptativo} aplique el factor en el mismo turno.
     *
     * @param red         grafo con el estado actual
     * @param turnoActual número del turno (para el log)
     * @return lista de eventos disparados en este turno (vacía si ninguno se activó)
     */
    public List<EventoEpidemiologico> evaluar(RedSocial red, int turnoActual) {
        List<EventoEpidemiologico> disparados = new ArrayList<>();
        int total = red.getTotalPersonas();
        if (total == 0) return disparados;

        int infectados = red.getPersonasPorEstado(EstadoSIRV.INFECTADO).size();
        double porcentaje = (double) infectados / total;

        for (EventoEpidemiologico evento : eventos) {
            if (!evento.estaDisparado() && porcentaje >= evento.getUmbral()) {
                factorAcumuladoEventos *= evento.getFactorMultiplicador();
                evento.disparar();
                disparados.add(evento);
                System.out.printf("  [Turno %2d] *** %s activado (%.0f%% infectados, "
                        + "aristas × %.2f, factor acumulado × %.3f) ***%n",
                        turnoActual, evento.getNombre(),
                        evento.getUmbral() * 100,
                        evento.getFactorMultiplicador(),
                        factorAcumuladoEventos);
            }
        }
        return disparados;
    }

    /** Factor producto de todos los eventos NPI disparados hasta ahora. Empieza en 1.0. */
    public double getFactorAcumuladoEventos() { return factorAcumuladoEventos; }

    public List<EventoEpidemiologico> getEventos() {
        return Collections.unmodifiableList(eventos);
    }
}
