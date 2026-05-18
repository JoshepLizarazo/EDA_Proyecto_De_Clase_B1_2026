package domain.algoritmo;

import domain.model.Contacto;
import domain.model.Persona;
import domain.model.RedSocial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Encuentra el camino de mayor probabilidad de contagio entre dos nodos.
 *
 * Analogía con Dijkstra:
 *   Dijkstra minimiza la SUMA de pesos en un grafo con distancias.
 *   BFSPonderado maximiza el PRODUCTO de probabilidades en el grafo de contagio.
 *   Para ello se invierte la lógica: en lugar de dist[v] = ∞ (mínimos), se
 *   inicializa prob[v] = 0.0 (máximos) y se relaja por producto en lugar de suma.
 *   El invariante es: prob[v] = mayor probabilidad acumulada de llegar a v desde origen.
 *
 * Fórmula de relajación (análoga a la de Dijkstra):
 *   nuevaProb = prob[u] × arista(u,v).probContagio
 *   Si nuevaProb > prob[v] → actualizar prob[v] y re-encolar v.
 *
 * Por qué usar cola de prioridad (max-heap):
 *   Se procesa primero el nodo con mayor probabilidad acumulada conocida.
 *   Como probContagio ∈ (0, 1], el producto solo puede decrecer o mantenerse,
 *   garantizando la misma propiedad de optimalidad que Dijkstra con pesos ≥ 0:
 *   la primera vez que se extrae un nodo de la cola, su prob es ya la máxima posible.
 *
 * Complejidad: O((N + M) log N) — igual que Dijkstra con cola de prioridad.
 *
 * Uso en VacunacionBFSPonderado:
 *   Se invoca desde K focos hipotéticos. Por cada foco se ejecuta un BFS completo
 *   hacia todos los demás nodos, acumulando scores por los nodos intermedios.
 *   Total: O(K × (N + M) log N). Con K = 5 (constante), esto es barato comparado
 *   con Betweenness (O(N²)).
 *
 * @see VacunacionBFSPonderado que orquesta múltiples llamadas a esta clase
 */
public class BFSPonderado {

    /**
     * Camino de mayor probabilidad acumulada de contagio desde origen hasta destino.
     * Retorna lista vacía si no existe ninguna ruta entre ambos nodos.
     *
     * @param red     red social con las aristas ponderadas por probContagio
     * @param origen  nodo de partida (paciente cero hipotético)
     * @param destino nodo al que se quiere llegar
     * @return lista de nodos en orden origen → ... → destino; vacía si no hay ruta
     */
    public List<Persona> caminoMayorContagio(RedSocial red, Persona origen, Persona destino) {
        // prob[v] = mayor probabilidad acumulada conocida hasta v (0.0 = no alcanzado)
        Map<Persona, Double> prob = new HashMap<>();
        // anterior[v] = predecesor de v en el camino óptimo (para reconstruir la ruta)
        Map<Persona, Persona> anterior = new HashMap<>();

        for (Persona p : red.getTodasLasPersonas()) prob.put(p, 0.0);
        prob.put(origen, 1.0); // El origen se alcanza a sí mismo con certeza

        // Max-heap: extrae el nodo con mayor probabilidad acumulada primero
        PriorityQueue<Persona> cola = new PriorityQueue<>(
                Comparator.comparingDouble(p -> -prob.getOrDefault(p, 0.0)));
        cola.add(origen);

        while (!cola.isEmpty()) {
            Persona v = cola.poll();
            if (v.equals(destino)) break;

            for (Contacto c : red.getContactos(v)) {
                Persona w = c.getDestino();
                double nuevaProb = prob.get(v) * c.getProbContagio();
                if (nuevaProb > prob.getOrDefault(w, 0.0)) {
                    prob.put(w, nuevaProb);
                    anterior.put(w, v);
                    cola.add(w);
                }
            }
        }

        // Reconstruir camino desde destino hacia origen
        if (prob.getOrDefault(destino, 0.0) == 0.0) return new ArrayList<>();

        List<Persona> camino = new ArrayList<>();
        Persona actual = destino;
        while (actual != null) {
            camino.add(actual);
            actual = anterior.get(actual);
        }
        Collections.reverse(camino);
        return camino;
    }

    /**
     * Probabilidad máxima acumulada del camino de mayor contagio entre origen y destino.
     * Retorna 0.0 si no hay ruta.
     */
    public double probMaxima(RedSocial red, Persona origen, Persona destino) {
        List<Persona> camino = caminoMayorContagio(red, origen, destino);
        if (camino.isEmpty()) return 0.0;

        double prob = 1.0;
        for (int i = 0; i < camino.size() - 1; i++) {
            Persona u = camino.get(i);
            Persona v = camino.get(i + 1);
            for (Contacto c : red.getContactos(u)) {
                if (c.getDestino().equals(v)) {
                    prob *= c.getProbContagio();
                    break;
                }
            }
        }
        return prob;
    }
}
