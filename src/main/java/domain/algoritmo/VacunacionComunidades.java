package domain.algoritmo;

import domain.model.Contacto;
import domain.model.Persona;
import domain.model.RedSocial;
import domain.value.EstadoSIRV;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Estrategia de vacunación dentro de comunidades.
 *
 * Fundamento: en redes con estructura de comunidades, la propagación ocurre
 * primero y más rápido dentro del grupo. Vacunar en las comunidades más densas
 * interrumpe los contagios intra-grupo antes de que la epidemia pueda saltar
 * hacia otras comunidades a través de los puentes.
 *
 * Definición de comunidad usada:
 *   Se aproxima comunidad = estrato socioeconómico. En la red colombiana generada
 *   por GeneradorPoblacion, los clusters familiares y vecinales se forman dentro
 *   del mismo estrato, por lo que el estrato es un proxy válido del grupo social.
 *
 * Algoritmo (4 pasos):
 *
 *   1. Agrupar todos los nodos SUSCEPTIBLE por estrato (1–6).
 *
 *   2. Calcular la densidad de aristas internas de cada grupo:
 *        densidad(g) = aristasInternas(g) / (|g| × (|g| - 1))
 *      Una densidad alta indica que el grupo es un foco de contagio activo.
 *
 *   3. Ordenar los grupos de mayor a menor densidad.
 *
 *   4. Vacunar desde el grupo más denso hacia el menos denso.
 *      Dentro de cada grupo, priorizar por grado (más conectado primero),
 *      hasta completar el 20% de la población susceptible total.
 *
 * Complejidad:
 *   Paso 1: O(N)
 *   Paso 2: O(N + M)  — recorre aristas para contar las internas
 *   Paso 3: O(K log K) — K = número de estratos distintos (máximo 6)
 *   Paso 4: O(N log N) — ordenamiento por grado dentro de cada grupo
 *   Total:  O(N + M)
 *
 * Ventaja:  efectiva en redes con comunidades densas y bien separadas.
 *           Corta la propagación intra-grupo antes de que salte entre grupos.
 * Desventaja: no bloquea los puentes inter-comunidad (para eso es Betweenness).
 *             En redes muy homogéneas, su resultado se acerca a Aleatoria.
 *
 * Comparación con Híbrida:
 *   Comunidades opera a nivel de GRUPO (decide qué comunidad priorizar primero).
 *   Híbrida opera a nivel de INDIVIDUO (score compuesto por cada nodo).
 *   Son complementarias: Comunidades es más interpretable; Híbrida, más precisa.
 *
 * @see VacunacionService que delega aquí cuando la estrategia es COMUNIDADES
 * @see VacunacionHibrida para la estrategia a nivel individual del equipo
 */
public class VacunacionComunidades {

    private static final double PORCENTAJE_VACUNACION = 0.20;

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Vacuna el 20% de la población susceptible, priorizando comunidades (estratos)
     * con mayor densidad interna de aristas.
     *
     * @param red red social sobre la que se aplica la vacunación
     * @return lista de personas que fueron vacunadas
     */
    public List<Persona> vacunar(RedSocial red) {
        List<Persona> susceptibles = red.getPersonasPorEstado(EstadoSIRV.SUSCEPTIBLE);
        int cuota = (int) Math.floor(PORCENTAJE_VACUNACION * susceptibles.size());

        // Paso 1: agrupar susceptibles por estrato
        Map<Integer, List<Persona>> grupos = agruparPorEstrato(susceptibles);

        // Paso 2: calcular densidad interna de cada grupo
        Map<Integer, Double> densidades = calcularDensidades(grupos, red);

        // Paso 3: ordenar estratos de mayor a menor densidad
        List<Integer> estratosOrdenados = new ArrayList<>(grupos.keySet());
        estratosOrdenados.sort((a, b) -> Double.compare(densidades.get(b), densidades.get(a)));

        // Paso 4: vacunar desde el grupo más denso, priorizando por grado dentro del grupo
        List<Persona> vacunados = new ArrayList<>();
        for (int estrato : estratosOrdenados) {
            if (vacunados.size() >= cuota) break;

            List<Persona> miembros = new ArrayList<>(grupos.get(estrato));
            // Dentro del grupo, vacunar primero a los más conectados
            miembros.sort(Comparator.comparingInt(p -> -red.getGrado(p)));

            for (Persona p : miembros) {
                if (vacunados.size() >= cuota) break;
                p.setEstado(EstadoSIRV.VACUNADO);
                vacunados.add(p);
            }
        }

        return vacunados;
    }

    /**
     * Expone las densidades calculadas por grupo (útil para análisis y logs).
     * Puede llamarse antes de vacunar() para inspeccionar la estructura de la red.
     *
     * @param red red social a analizar
     * @return mapa estrato → densidad interna [0.0, 1.0]
     */
    public Map<Integer, Double> calcularDensidadesPorEstrato(RedSocial red) {
        List<Persona> susceptibles = red.getPersonasPorEstado(EstadoSIRV.SUSCEPTIBLE);
        Map<Integer, List<Persona>> grupos = agruparPorEstrato(susceptibles);
        return calcularDensidades(grupos, red);
    }

    // ── Métodos auxiliares ────────────────────────────────────────────────────

    /** Agrupa la lista de personas en un mapa estrato → lista de miembros. */
    private Map<Integer, List<Persona>> agruparPorEstrato(List<Persona> personas) {
        Map<Integer, List<Persona>> grupos = new HashMap<>();
        for (Persona p : personas) {
            grupos.computeIfAbsent(p.getEstrato(), k -> new ArrayList<>()).add(p);
        }
        return grupos;
    }

    /**
     * Calcula la densidad de aristas internas para cada grupo.
     *
     * densidad(g) = aristasInternas(g) / (|g| × (|g| - 1))
     *
     * Se cuentan solo las aristas cuyo origen Y destino pertenecen al mismo grupo.
     * El denominador es el máximo de aristas posibles en un grafo dirigido sin lazos.
     */
    private Map<Integer, Double> calcularDensidades(Map<Integer, List<Persona>> grupos,
                                                     RedSocial red) {
        Map<Integer, Double> densidades = new HashMap<>();

        for (Map.Entry<Integer, List<Persona>> entry : grupos.entrySet()) {
            int estrato = entry.getKey();
            List<Persona> miembros = entry.getValue();
            int n = miembros.size();

            if (n < 2) {
                densidades.put(estrato, 0.0);
                continue;
            }

            // Set para verificar en O(1) si un nodo pertenece al grupo
            Set<Persona> set = new HashSet<>(miembros);

            long aristasInternas = 0;
            for (Persona p : miembros) {
                for (Contacto c : red.getContactos(p)) {
                    if (set.contains(c.getDestino())) {
                        aristasInternas++;
                    }
                }
            }

            // Máximo de aristas dirigidas sin lazos en un grupo de n nodos: n*(n-1)
            double densidad = (double) aristasInternas / ((long) n * (n - 1));
            densidades.put(estrato, densidad);
        }

        return densidades;
    }
}
