package domain.algoritmo;

import domain.model.Contacto;
import domain.model.Persona;
import domain.model.RedSocial;
import domain.value.EstadoSIRV;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Vacunación por centralidad de intermediación (Brandes algorithm). O(N × (N+M)).
 * Vacuna el 20% de nodos susceptibles con mayor betweenness — los puentes entre comunidades.
 */
public class VacunacionBetweenness {

    private static final double PORCENTAJE = 0.20;

    /**
     * Calcula el betweenness de todos los nodos usando el algoritmo de Brandes.
     * Opera sobre el grafo dirigido.
     */
    public Map<Persona, Double> calcularBetweenness(RedSocial red) {
        Collection<Persona> nodos = red.getTodasLasPersonas();
        Map<Persona, Double> cb = new HashMap<>();
        for (Persona p : nodos) cb.put(p, 0.0);

        for (Persona s : nodos) {
            // σ[v] = número de caminos más cortos desde s hasta v
            Map<Persona, Double> sigma = new HashMap<>();
            // d[v]  = distancia desde s hasta v (-1 = no visitado)
            Map<Persona, Integer> d = new HashMap<>();
            // pred[v] = predecesores de v en caminos más cortos desde s
            Map<Persona, List<Persona>> pred = new HashMap<>();

            for (Persona p : nodos) {
                sigma.put(p, 0.0);
                d.put(p, -1);
                pred.put(p, new ArrayList<>());
            }
            sigma.put(s, 1.0);
            d.put(s, 0);

            Queue<Persona> cola = new LinkedList<>();
            Deque<Persona> pila = new ArrayDeque<>();
            cola.add(s);

            // BFS hacia adelante
            while (!cola.isEmpty()) {
                Persona v = cola.poll();
                pila.push(v);
                for (Contacto c : red.getContactos(v)) {
                    Persona w = c.getDestino();
                    // Primera visita a w
                    if (d.get(w) < 0) {
                        cola.add(w);
                        d.put(w, d.get(v) + 1);
                    }
                    // Camino más corto a w pasa por v
                    if (d.get(w).equals(d.get(v) + 1)) {
                        sigma.merge(w, sigma.get(v), Double::sum);
                        pred.get(w).add(v);
                    }
                }
            }

            // Retropropagación (acumulación de dependencias)
            Map<Persona, Double> delta = new HashMap<>();
            for (Persona p : nodos) delta.put(p, 0.0);

            while (!pila.isEmpty()) {
                Persona w = pila.pop();
                for (Persona v : pred.get(w)) {
                    double aporte = (sigma.get(v) / sigma.get(w)) * (1.0 + delta.get(w));
                    delta.merge(v, aporte, Double::sum);
                }
                if (!w.equals(s)) {
                    cb.merge(w, delta.get(w), Double::sum);
                }
            }
        }
        return cb;
    }

    public List<Persona> vacunar(RedSocial red) {
        Map<Persona, Double> cb = calcularBetweenness(red);
        List<Persona> susceptibles = new ArrayList<>(red.getPersonasPorEstado(EstadoSIRV.SUSCEPTIBLE));
        susceptibles.sort((a, b) -> Double.compare(cb.getOrDefault(b, 0.0), cb.getOrDefault(a, 0.0)));

        int cuota = (int) Math.floor(PORCENTAJE * susceptibles.size());
        List<Persona> vacunados = new ArrayList<>();
        for (int i = 0; i < cuota; i++) {
            susceptibles.get(i).setEstado(EstadoSIRV.VACUNADO);
            vacunados.add(susceptibles.get(i));
        }
        return vacunados;
    }
}
