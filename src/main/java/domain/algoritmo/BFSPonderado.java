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
 * Algoritmo tipo Dijkstra donde la "distancia" es la probabilidad acumulada (maximizar).
 * O((N + M) log N).
 */
public class BFSPonderado {

    /**
     * Camino de mayor probabilidad acumulada de contagio desde origen hasta destino.
     * Retorna lista vacía si no existe ruta.
     */
    public List<Persona> caminoMayorContagio(RedSocial red, Persona origen, Persona destino) {
        // prob[v] = mejor probabilidad acumulada conocida hasta v
        Map<Persona, Double> prob = new HashMap<>();
        // antecesor[v] = nodo anterior en el camino óptimo
        Map<Persona, Persona> anterior = new HashMap<>();

        for (Persona p : red.getTodasLasPersonas()) prob.put(p, 0.0);
        prob.put(origen, 1.0);

        // Cola de prioridad: mayor prob primero
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
