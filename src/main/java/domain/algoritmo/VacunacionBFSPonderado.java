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
 * Algoritmo:
 *   1. Selecciona K candidatos a paciente cero — los nodos de mayor grado (hubs),
 *      que son los más expuestos a ser focos iniciales.
 *   2. Desde cada candidato, ejecuta {@link BFSPonderado} hacia todos los demás
 *      nodos para reconstruir el camino de mayor probabilidad acumulada.
 *   3. Acumula un score por nodo: cada vez que aparece en un camino, suma la
 *      probabilidad acumulada de ese camino. Así los nodos que están en muchas
 *      rutas críticas reciben score alto.
 *   4. Vacuna el 20% con mayor score.
 *
 * Complejidad:
 *   O(K × N × (N+M) log N) — dominado por K BFS-ponderados. Con K pequeño (≈ 5)
 *   es claramente más barato que Betweenness pero captura una señal similar:
 *   importancia estructural en la propagación.
 */
public class VacunacionBFSPonderado {

    private static final double PORCENTAJE = 0.20;
    private static final int CANDIDATOS_PACIENTE_CERO = 5;

    public List<Persona> vacunar(RedSocial red) {
        List<Persona> susceptibles = new ArrayList<>(red.getPersonasPorEstado(EstadoSIRV.SUSCEPTIBLE));
        if (susceptibles.isEmpty()) return new ArrayList<>();

        // 1. Seleccionar candidatos a paciente cero por grado descendente
        List<Persona> candidatos = new ArrayList<>(susceptibles);
        candidatos.sort(Comparator.comparingInt((Persona p) -> red.getGrado(p)).reversed());
        int k = Math.min(CANDIDATOS_PACIENTE_CERO, candidatos.size());
        List<Persona> focos = candidatos.subList(0, k);

        // 2. Para cada foco, recorrer todos los nodos y acumular score por aparición en rutas
        BFSPonderado bfs = new BFSPonderado();
        Map<Persona, Double> scores = new HashMap<>();
        for (Persona p : susceptibles) scores.put(p, 0.0);

        for (Persona foco : focos) {
            for (Persona destino : susceptibles) {
                if (destino.equals(foco)) continue;
                List<Persona> camino = bfs.caminoMayorContagio(red, foco, destino);
                if (camino.size() < 2) continue;
                double prob = bfs.probMaxima(red, foco, destino);
                // Sumar prob a cada nodo intermedio (excluir foco y destino para
                // que el score refleje rol de "puente" o "nodo de paso")
                for (int i = 1; i < camino.size() - 1; i++) {
                    Persona intermedio = camino.get(i);
                    scores.merge(intermedio, prob, Double::sum);
                }
            }
        }

        // 3. Ordenar por score descendente y vacunar el 20%
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
