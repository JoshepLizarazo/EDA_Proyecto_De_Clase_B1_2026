package domain.algoritmo;

import domain.model.Persona;
import domain.model.RedSocial;
import domain.value.EstadoSIRV;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Vacunación por hubs (centralidad de grado). O(N log N).
 * Vacuna el 20% con mayor número de conexiones salientes.
 */
public class VacunacionHubs {

    private static final double PORCENTAJE = 0.20;

    public List<Persona> vacunar(RedSocial red) {
        List<Persona> susceptibles = new ArrayList<>(red.getPersonasPorEstado(EstadoSIRV.SUSCEPTIBLE));
        // Orden descendente por grado saliente
        susceptibles.sort(Comparator.comparingInt(p -> -red.getGrado(p)));

        int cuota = (int) Math.floor(PORCENTAJE * susceptibles.size());
        List<Persona> vacunados = new ArrayList<>();
        for (int i = 0; i < cuota; i++) {
            susceptibles.get(i).setEstado(EstadoSIRV.VACUNADO);
            vacunados.add(susceptibles.get(i));
        }
        return vacunados;
    }
}
