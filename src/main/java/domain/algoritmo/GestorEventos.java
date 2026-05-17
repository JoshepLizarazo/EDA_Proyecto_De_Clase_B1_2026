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
 * Evalúa en cada turno si el porcentaje de infectados superó algún umbral
 * y dispara el evento correspondiente, multiplicando el peso de todas las aristas.
 *
 * Cada evento solo se dispara una vez por simulación.
 */
public class GestorEventos {

    private final List<EventoEpidemiologico> eventos;

    public GestorEventos() {
        eventos = new ArrayList<>();
        // Tres eventos predefinidos según el modelo epidemiológico del proyecto
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
