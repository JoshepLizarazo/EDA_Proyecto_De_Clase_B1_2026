package domain.algoritmo;

import domain.model.Contacto;
import domain.model.Persona;
import domain.model.RedSocial;
import domain.value.EstadoSIRV;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Implementa la propagación de la epidemia turno a turno (modelo SIRV discreto).
 *
 * Orden de operaciones por turno (sincrónico — evita que un nodo recién
 * infectado pueda contagiar en el mismo turno que fue infectado):
 *   1. Recolectar nuevosInfectados: por cada INFECTADO y cada vecino SUSCEPTIBLE,
 *      lanzar r ∈ [0,1); si r < probContagio → añadir vecino al set.
 *   2. Recolectar nuevosRecuperados: incrementar diasInfectado de cada INFECTADO;
 *      si diasInfectado >= diasRecuperacion → añadir a la lista.
 *   3. Aplicar cambios de estado (fuera del recorrido principal).
 *   4. Contar y retornar {S, I, R, V}.
 */
public class ModeloSIRV {

    private final int diasRecuperacion;
    private final Random random;

    public ModeloSIRV(int diasRecuperacion, long semilla) {
        this.diasRecuperacion = diasRecuperacion;
        this.random = new Random(semilla);
    }

    /**
     * Ejecuta un turno completo de simulación sobre la red.
     *
     * @param red red social con el estado actual de todos los nodos
     * @return mapa con conteo de nodos por estado {"S", "I", "R", "V"}
     */
    public Map<String, Integer> simularTurno(RedSocial red) {
        Collection<Persona> todas = red.getTodasLasPersonas();

        // Paso 1: recolectar quiénes se infectan este turno
        // Se usa Set para que una persona no se agregue dos veces si tiene varios vecinos infectados
        Set<Persona> nuevosInfectados = new HashSet<>();
        for (Persona p : todas) {
            if (p.getEstado() != EstadoSIRV.INFECTADO) continue;
            for (Contacto c : red.getContactos(p)) {
                Persona vecino = c.getDestino();
                if (vecino.getEstado() == EstadoSIRV.SUSCEPTIBLE
                        && !nuevosInfectados.contains(vecino)
                        && random.nextDouble() < c.getProbContagio()) {
                    nuevosInfectados.add(vecino);
                }
            }
        }

        // Paso 2: recolectar quiénes se recuperan este turno
        List<Persona> nuevosRecuperados = new ArrayList<>();
        for (Persona p : todas) {
            if (p.getEstado() != EstadoSIRV.INFECTADO) continue;
            p.incrementarDiasInfectado();
            if (p.getDiasInfectado() >= diasRecuperacion) {
                nuevosRecuperados.add(p);
            }
        }

        // Paso 3: aplicar cambios de estado
        for (Persona p : nuevosInfectados) {
            // Doble verificación: sigue siendo SUSCEPTIBLE (no se recuperó este turno)
            if (p.getEstado() == EstadoSIRV.SUSCEPTIBLE) {
                p.setEstado(EstadoSIRV.INFECTADO);
                p.setDiasInfectado(0);
            }
        }
        for (Persona p : nuevosRecuperados) {
            p.setEstado(EstadoSIRV.RECUPERADO);
        }

        return contarEstados(red);
    }

    /** Cuenta cuántos nodos hay en cada estado SIRV. */
    private Map<String, Integer> contarEstados(RedSocial red) {
        Map<String, Integer> conteo = new LinkedHashMap<>();
        conteo.put("S", 0);
        conteo.put("I", 0);
        conteo.put("R", 0);
        conteo.put("V", 0);
        for (Persona p : red.getTodasLasPersonas()) {
            switch (p.getEstado()) {
                case SUSCEPTIBLE -> conteo.merge("S", 1, Integer::sum);
                case INFECTADO   -> conteo.merge("I", 1, Integer::sum);
                case RECUPERADO  -> conteo.merge("R", 1, Integer::sum);
                case VACUNADO    -> conteo.merge("V", 1, Integer::sum);
            }
        }
        return conteo;
    }
}
