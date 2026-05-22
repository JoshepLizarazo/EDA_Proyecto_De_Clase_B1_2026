package domain.algoritmo;

import domain.model.Contacto;
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
 * Fundamento epidemiológico:
 *   En una epidemia real, los gobiernos reaccionan cuando la carga de infectados
 *   supera ciertos umbrales. Estas intervenciones reducen la probabilidad de contagio
 *   de todos los contactos (aristas) de la red: la gente se aísla, usa tapabocas,
 *   evita lugares concurridos. Modelamos esto multiplicando el peso de TODAS las
 *   aristas por un factor reductor cuando se supera cada umbral.
 *
 * Tres eventos predefinidos (en orden creciente de umbral):
 *
 *   ALERTA_LEVE (umbral 30%, factor 0.80):
 *     Recomendaciones básicas de bioseguridad. Reduce contagio un 20%.
 *
 *   CUARENTENA (umbral 50%, factor 0.50):
 *     Medidas de distanciamiento social, teletrabajo, cierres parciales.
 *     Reduce contagio a la mitad respecto del valor actual de la arista.
 *     Nota: los factores son ACUMULATIVOS. Si ya se aplicó ALERTA_LEVE (×0.80)
 *     y luego CUARENTENA (×0.50), la prob final es la original × 0.80 × 0.50 = × 0.40.
 *
 *   LOCKDOWN (umbral 70%, factor 0.20):
 *     Confinamiento estricto. Reduce contagio al 20% del valor actual.
 *     Combinado con los anteriores: prob final = original × 0.80 × 0.50 × 0.20 = × 0.08.
 *
 * Cada evento solo se dispara UNA VEZ por simulación (flag estaDisparado()).
 * La evaluación se hace turno a turno desde IniciarSimulacionCommand.
 *
 * Efecto en la simulación:
 *   Al dispararse un evento, se reducen los pesos de las aristas de la red.
 *   Esto hace que la simulación adapte dinámicamente el ritmo de propagación,
 *   reflejando cómo las medidas sociales "aplanan la curva" de infectados.
 *
 * @see ModeloSIRV que usa las probContagio de las aristas en cada turno
 * @see IniciarSimulacionCommand que llama evaluar() después de cada simularTurno()
 */
public class GestorEventos {

    private final List<EventoEpidemiologico> eventos;

    public GestorEventos() {
        eventos = new ArrayList<>();
        // Umbrales y factores calibrados para reflejar respuestas NPI colombianas
        eventos.add(new EventoEpidemiologico(TipoEvento.ALERTA_LEVE, 0.30, 0.80, "Alerta Leve"));
        eventos.add(new EventoEpidemiologico(TipoEvento.CUARENTENA,  0.50, 0.50, "Cuarentena"));
        eventos.add(new EventoEpidemiologico(TipoEvento.LOCKDOWN,    0.70, 0.20, "Lockdown"));
    }

    /**
     * Evalúa si algún umbral fue superado y dispara los eventos correspondientes.
     * Modifica los pesos de todas las aristas de la red si se activa un evento.
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
                // Aplicar factor a todas las aristas del grafo
                for (Contacto c : red.getTodosLosContactos()) {
                    c.aplicarFactor(evento.getFactorMultiplicador());
                }
                evento.disparar();
                disparados.add(evento);
                System.out.printf("  [Turno %2d] *** %s activado (%.0f%% infectados, aristas × %.2f) ***%n",
                        turnoActual, evento.getNombre(),
                        evento.getUmbral() * 100, evento.getFactorMultiplicador());
            }
        }
        return disparados;
    }

    public List<EventoEpidemiologico> getEventos() {
        return Collections.unmodifiableList(eventos);
    }
}
