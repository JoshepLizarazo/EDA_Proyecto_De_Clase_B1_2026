package domain.algoritmo;

import domain.model.Persona;
import domain.model.RedSocial;
import domain.value.EstadoSIRV;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Vacunación por hubs — centralidad de grado.
 *
 * Fundamento: los nodos con más conexiones salientes (hubs) tienen mayor
 * capacidad de contagiar a muchas personas en un mismo turno. Si un hub
 * se vacuna, se eliminan todas sus aristas de contagio de golpe, reduciendo
 * fuertemente el R₀ local de la red.
 *
 * Intuición epidemiológica:
 *   En redes con distribución de grado heterogénea (donde pocos nodos concentran
 *   muchas conexiones, como ocurre en redes sociales reales), el umbral de
 *   propagación epidémica depende fuertemente de los hubs. Inmunizar el 20% con
 *   mayor grado puede tener un efecto equivalente a inmunizar el 60-70% de forma
 *   aleatoria, porque se cortan los nodos que más aristas de contagio aportan.
 *
 * Algoritmo (3 pasos):
 *   1. Obtener todos los nodos en estado SUSCEPTIBLE.
 *   2. Ordenar por grado saliente de mayor a menor.
 *      getGrado(p) es O(1): devuelve el tamaño de la lista de adyacencia.
 *   3. Vacunar el 20% con mayor grado.
 *
 * Complejidad: O(N log N) — dominado por el ordenamiento.
 *   Obtener grado es O(1) por nodo, así que el paso 1 es O(N).
 *
 * Ventaja:  muy rápida y efectiva en redes con hubs pronunciados.
 *           Fácil de justificar: "vacunar primero a los más conectados".
 * Desventaja: puede ignorar nodos con grado moderado pero posición estratégica
 *             (puentes inter-comunidad), que Betweenness sí detecta.
 *             No considera el perfil de riesgo personal (edad, estrato).
 *
 * Comparación con las demás estrategias:
 *   Aleatoria   → sin información de red (O(N))
 *   Hubs        → conectividad local de cada nodo (O(N log N))    ← esta
 *   Betweenness → posición global en la red, detecta puentes (O(N²))
 *   Híbrida     → score compuesto: red + perfil individual (O(N²))
 *
 * @see VacunacionBetweenness para detectar puentes (distintos a hubs por grado)
 * @see VacunacionHibrida que combina grado con betweenness, edad y estrato
 */
public class VacunacionHubs {

    private static final double PORCENTAJE = 0.20;

    /**
     * Vacuna el 20% de la población susceptible con mayor grado saliente.
     *
     * @param red red social sobre la que se aplica la vacunación
     * @return lista de personas que fueron vacunadas
     */
    public List<Persona> vacunar(RedSocial red) {
        List<Persona> susceptibles = new ArrayList<>(red.getPersonasPorEstado(EstadoSIRV.SUSCEPTIBLE));
        // Orden descendente por grado saliente: los más conectados primero
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
