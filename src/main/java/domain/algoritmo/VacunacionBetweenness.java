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
 * Vacunación por centralidad de intermediación — algoritmo de Brandes.
 *
 * Fundamento: en redes con comunidades, los contagios que saltan de un grupo
 * a otro pasan necesariamente por nodos "puente" de alta intermediación.
 * Vacunar esos puentes contiene la epidemia dentro de su comunidad de origen,
 * aunque no detiene la propagación interna de cada grupo.
 *
 * Definición de betweenness centrality:
 *   CB(v) = Σ_{s≠v≠t} [ σ(s,t|v) / σ(s,t) ]
 *   donde:
 *     σ(s,t)   = número de caminos más cortos entre s y t (en el grafo dirigido)
 *     σ(s,t|v) = cuántos de esos caminos pasan por v
 *   Un CB alto indica que v es "puente" crítico: si se vacuna, muchas rutas
 *   de propagación quedan bloqueadas sin pasar por él.
 *
 * Algoritmo de Brandes (2001) — estándar para betweenness exacto:
 *   Para cada nodo fuente s ejecutar dos fases:
 *
 *   Fase 1 — BFS hacia adelante:
 *     Calcula σ[v] = número de caminos mínimos desde s hasta v,
 *     d[v]   = distancia desde s hasta v,
 *     pred[v] = lista de predecesores de v en esos caminos.
 *     Simultáneamente, apila los nodos en orden BFS (pila invertida).
 *
 *   Fase 2 — Retropropagación (back-propagation):
 *     Recorre la pila de atrás hacia adelante y acumula la dependencia:
 *       δ[v] += (σ[v] / σ[w]) × (1 + δ[w])   para cada predecesor v de w
 *     Al final: CB[w] += δ[w]  (para w ≠ s).
 *     La dependencia δ[w] mide cuántos pares (s, t) tienen a w como
 *     intermediario necesario en sus caminos más cortos desde s.
 *
 *   Opera sobre el grafo DIRIGIDO: la dirección modela quién puede contagiar a quién.
 *
 * Complejidad: O(N × (N + M))
 *   N ejecuciones de BFS (una por nodo fuente), cada una O(N + M).
 *   En la red generada (N ≈ 100–500, M ≈ N×5), esto es manejable.
 *
 * Ventaja:  detecta puentes inter-comunidad que ninguna métrica local (grado)
 *           revelaría. Excelente cuando la epidemia amenaza con saltar entre grupos.
 * Desventaja: la más costosa computacionalmente (O(N²) en el mejor caso).
 *             No considera vulnerabilidad epidemiológica individual del nodo.
 *
 * Comparación con las demás estrategias:
 *   Hubs        → conectividad local (¿cuántos vecinos directos tiene?)
 *   Betweenness → posición global (¿cuántos pares de nodos dependen de él?)  ← esta
 *   Comunidades → grupo más denso (¿en qué estrato hay más contagio intra-grupo?)
 *   Híbrida     → score compuesto que reutiliza este betweenness + otros factores
 *
 * @see VacunacionHibrida que reutiliza calcularBetweenness() para construir su score
 * @see VacunacionComunidades estrategia complementaria que ataca contagio intra-grupo
 */
public class VacunacionBetweenness {

    private static final double PORCENTAJE = 0.20;

    /**
     * Calcula el betweenness de todos los nodos usando el algoritmo de Brandes.
     * Opera sobre el grafo dirigido de la red.
     *
     * @param red red social a analizar
     * @return mapa nodo → valor de betweenness (sin normalizar)
     */
    public Map<Persona, Double> calcularBetweenness(RedSocial red) {
        Collection<Persona> nodos = red.getTodasLasPersonas();
        Map<Persona, Double> cb = new HashMap<>();
        for (Persona p : nodos) cb.put(p, 0.0);

        for (Persona s : nodos) {
            // σ[v] = número de caminos más cortos desde s hasta v
            Map<Persona, Double> sigma = new HashMap<>();
            // d[v] = distancia BFS desde s hasta v (-1 = no visitado todavía)
            Map<Persona, Integer> d = new HashMap<>();
            // pred[v] = predecesores de v en los caminos más cortos desde s
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

            // ── Fase 2: retropropagación — acumular dependencias ─────────────
            // δ[v] acumula la fracción de pares (s,t) para los que v es intermediario.
            // Fórmula: δ[v] += (σ[v] / σ[w]) × (1 + δ[w])  para cada predecesor v de w.
            Map<Persona, Double> delta = new HashMap<>();
            for (Persona p : nodos) delta.put(p, 0.0);

            while (!pila.isEmpty()) {
                Persona w = pila.pop();
                for (Persona v : pred.get(w)) {
                    // Proporción de caminos mínimos desde s hasta w que pasan por v
                    double aporte = (sigma.get(v) / sigma.get(w)) * (1.0 + delta.get(w));
                    delta.merge(v, aporte, Double::sum);
                }
                // El nodo fuente s no acumula betweenness (no intermedia entre él y otro)
                if (!w.equals(s)) {
                    cb.merge(w, delta.get(w), Double::sum);
                }
            }
        }
        return cb;
    }

    /**
     * Vacuna el 20% de susceptibles con mayor betweenness centrality.
     *
     * @param red red social sobre la que se aplica la vacunación
     * @return lista de personas que fueron vacunadas
     */
    public List<Persona> vacunar(RedSocial red) {
        Map<Persona, Double> cb = calcularBetweenness(red);
        List<Persona> susceptibles = new ArrayList<>(red.getPersonasPorEstado(EstadoSIRV.SUSCEPTIBLE));
        // Ordenar de mayor a menor betweenness: los puentes primero
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
