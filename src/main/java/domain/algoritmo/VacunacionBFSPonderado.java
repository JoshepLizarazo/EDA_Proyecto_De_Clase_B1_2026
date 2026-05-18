package domain.algoritmo;

import domain.model.Persona;
import domain.model.RedSocial;
import domain.value.EstadoSIRV;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vacunación basada en caminos de mayor probabilidad de contagio (BFS Ponderado).
 *
 * Idea: simula desde dónde podría empezar el brote y vacuna los nodos que aparecen
 * con mayor frecuencia (ponderada por probabilidad) en las rutas de propagación más
 * probables desde esos focos hipotéticos.
 *
 * Algoritmo (4 pasos):
 *   1. Seleccionar K candidatos a paciente cero: los nodos de mayor grado (hubs),
 *      que son los más expuestos a recibir el contagio inicial.
 *   2. Desde cada candidato, ejecutar {@link BFSPonderado} hacia cada uno de los
 *      demás susceptibles para obtener el camino de mayor probabilidad acumulada.
 *   3. Acumular score por nodo: cada vez que un nodo aparece como INTERMEDIARIO
 *      en un camino crítico, se le suma la probabilidad acumulada de ese camino.
 *      Los nodos en muchas rutas críticas reciben score alto.
 *      (Se excluyen origen y destino: solo interesan los nodos de paso.)
 *   4. Ordenar por score descendente y vacunar el 20%.
 *
 * Por qué excluir origen y destino del score:
 *   El objetivo es identificar nodos que actúan de "puente de contagio" entre
 *   el foco y el resto de la red. El foco ya es conocido (es el candidato) y
 *   el destino final no es intermediario de sí mismo.
 *
 * Complejidad:
 *   O(K × N × (N+M) log N) — K BFS-ponderados por N destinos, cada uno O((N+M) log N).
 *   Con K = 5 (constante pequeña), es notablemente más barato que Betweenness
 *   (O(N²)) pero captura una señal parecida: importancia de un nodo en la propagación.
 */
public class VacunacionBFSPonderado {

    private static final double PORCENTAJE = 0.20;
    private static final int CANDIDATOS_PACIENTE_CERO = 5;

    /**
     * Vacuna el 20% de susceptibles con mayor score de aparición en rutas críticas.
     *
     * @param red red social sobre la que se aplica la vacunación
     * @return lista de personas que fueron vacunadas
     */
    public List<Persona> vacunar(RedSocial red) {
        List<Persona> susceptibles = new ArrayList<>(red.getPersonasPorEstado(EstadoSIRV.SUSCEPTIBLE));
        if (susceptibles.isEmpty()) return new ArrayList<>();

        // Paso 1: seleccionar candidatos a paciente cero por grado descendente
        List<Persona> candidatos = new ArrayList<>(susceptibles);
        candidatos.sort(Comparator.comparingInt((Persona p) -> red.getGrado(p)).reversed());
        int k = Math.min(CANDIDATOS_PACIENTE_CERO, candidatos.size());
        List<Persona> focos = candidatos.subList(0, k);

        // Paso 2: para cada foco, recorrer todos los destinos y acumular score por aparición en rutas
        BFSPonderado bfs = new BFSPonderado();
        Map<Persona, Double> scores = new HashMap<>();
        for (Persona p : susceptibles) scores.put(p, 0.0);

        for (Persona foco : focos) {
            for (Persona destino : susceptibles) {
                if (destino.equals(foco)) continue;
                List<Persona> camino = bfs.caminoMayorContagio(red, foco, destino);
                if (camino.size() < 2) continue;
                double prob = bfs.probMaxima(red, foco, destino);
                // Solo los intermedios suman: foco y destino no reflejan rol de "puente"
                for (int i = 1; i < camino.size() - 1; i++) {
                    Persona intermedio = camino.get(i);
                    scores.merge(intermedio, prob, Double::sum);
                }
            }
        }

        // Paso 3: ordenar por score descendente y vacunar el 20%
        susceptibles.sort((a, b) -> Double.compare(scores.get(b), scores.get(a)));

        int cuota = (int) Math.floor(PORCENTAJE * susceptibles.size());
        List<Persona> vacunados = new ArrayList<>();
        for (int i = 0; i < cuota; i++) {
            susceptibles.get(i).setEstado(EstadoSIRV.VACUNADO);
            vacunados.add(susceptibles.get(i));
        }
        return vacunados;
    }
}
