package domain.algoritmo;

import domain.model.Persona;
import domain.model.RedSocial;
import domain.value.EstadoSIRV;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Vacunación aleatoria — línea base de comparación. O(N).
 */
public class VacunacionAleatoria {

    private static final double PORCENTAJE = 0.20;
    private final Random random;

    public VacunacionAleatoria(long semilla) {
        this.random = new Random(semilla);
    }

    public List<Persona> vacunar(RedSocial red) {
        List<Persona> susceptibles = new ArrayList<>(red.getPersonasPorEstado(EstadoSIRV.SUSCEPTIBLE));
        Collections.shuffle(susceptibles, random);

        int cuota = (int) Math.floor(PORCENTAJE * susceptibles.size());
        List<Persona> vacunados = new ArrayList<>();
        for (int i = 0; i < cuota; i++) {
            susceptibles.get(i).setEstado(EstadoSIRV.VACUNADO);
            vacunados.add(susceptibles.get(i));
        }
        return vacunados;
    }
}
