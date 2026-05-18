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
 * Modelo SIRV — estados posibles de cada nodo:
 *   S (Susceptible) → puede infectarse si tiene contacto con un infectado.
 *   I (Infectado)   → puede contagiar a vecinos susceptibles en cada turno.
 *   R (Recuperado)  → inmune tras superar la infección; ya no contagia ni se infecta.
 *   V (Vacunado)    → inmune antes de la epidemia; no participa en la propagación.
 *
 * Parámetros del modelo:
 *   - diasRecuperacion: cuántos turnos permanece en estado I un nodo antes de pasar a R.
 *   - probContagio (por arista): no hay un β global. Cada arista tiene su propia
 *     probabilidad calibrada por edad, estrato y ocupación del nodo origen.
 *     Ver GeneradorPoblacion.calcularProbContagio() para la fórmula.
 *
 * Ejecución sincrónica (por lotes):
 *   Los cambios de estado se aplican TODOS al final del turno, no nodo a nodo.
 *   Esto evita el efecto "cascade": sin sincronía, un nodo infectado en el turno t
 *   podría contagiar a sus vecinos en el mismo turno t dependiendo del orden de
 *   iteración, lo que haría el resultado no determinista dado el mismo orden inicial.
 *
 * Orden de operaciones por turno:
 *   1. Recolectar nuevosInfectados: por cada INFECTADO y cada vecino SUSCEPTIBLE,
 *      lanzar r ∈ [0,1); si r < probContagio(arista) → añadir vecino al set.
 *      Se usa Set para que un susceptible con múltiples vecinos infectados
 *      solo se infecte una vez por turno.
 *   2. Recolectar nuevosRecuperados: incrementar diasInfectado de cada INFECTADO;
 *      si diasInfectado >= diasRecuperacion → pasar a RECUPERADO.
 *   3. Aplicar cambios de estado (fuera del recorrido anterior — sincronía).
 *   4. Contar y retornar {S, I, R, V}.
 *
 * Condición de parada (gestionada externamente):
 *   La simulación termina cuando I = 0 (sin infectados) o se alcanza el máximo de
 *   turnos configurado. ModeloSIRV solo ejecuta UN turno; el ciclo lo controla
 *   IniciarSimulacionCommand, que llama simularTurno() en cada iteración.
 *
 * Reproducibilidad:
 *   La semilla del Random está fijada en ConfiguracionDto, por lo que dos
 *   simulaciones con los mismos parámetros producen exactamente el mismo resultado.
 *
 * @see IniciarSimulacionCommand que orquesta los turnos y detecta la condición de parada
 * @see GeneradorPoblacion que calibra probContagio de cada arista al construir la red
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
