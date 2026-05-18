package domain.algoritmo;

import domain.model.Persona;
import domain.model.RedSocial;
import domain.value.EstadoSIRV;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Vacunación aleatoria — línea base de comparación.
 *
 * Fundamento: no usa ninguna información estructural de la red ni del perfil
 * de cada persona. Selecciona individuos al azar entre los susceptibles y los
 * vacuna. El único parámetro configurable es la semilla, que garantiza
 * reproducibilidad entre ejecuciones.
 *
 * Por qué existe esta estrategia:
 *   Toda estrategia inteligente debe superar a la aleatoria para tener valor
 *   práctico. Si Betweenness, Hubs o Comunidades no mejoran sobre la aleatoria,
 *   significa que la estructura de la red no aporta información útil para decidir
 *   a quién vacunar (lo cual sería inusual en redes sociales reales).
 *   La aleatoria sirve como COTA INFERIOR de calidad: si otra estrategia tiene
 *   peores resultados, hay un error en su implementación o en los parámetros.
 *
 * Algoritmo (3 pasos):
 *   1. Obtener todos los nodos en estado SUSCEPTIBLE.
 *   2. Barajar la lista aleatoriamente usando la semilla fijada.
 *   3. Vacunar los primeros 20%.
 *
 * Complejidad: O(N) — un shuffle en O(N) más un recorrido lineal.
 *
 * Ventaja:  extremadamente rápida; sirve como referencia de comparación neutral.
 * Desventaja: ignora la topología de la red y el riesgo individual de cada persona.
 *             En redes con hubs muy marcados, tiene un desempeño muy inferior a Hubs.
 *
 * @see VacunacionHubs siguiente nivel: usa el grado como heurística simple y efectiva.
 * @see VacunacionBetweenness estrategia más sofisticada que usa posición global en la red.
 */
public class VacunacionAleatoria {

    private static final double PORCENTAJE = 0.20;
    private final Random random;

    public VacunacionAleatoria(long semilla) {
        this.random = new Random(semilla);
    }

    /**
     * Vacuna el 20% de la población susceptible en orden aleatorio.
     *
     * @param red red social sobre la que se aplica la vacunación
     * @return lista de personas que fueron vacunadas
     */
    public List<Persona> vacunar(RedSocial red) {
        List<Persona> susceptibles = new ArrayList<>(red.getPersonasPorEstado(EstadoSIRV.SUSCEPTIBLE));
        // Barajar garantiza selección sin sesgo estructural
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
