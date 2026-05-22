package domain.value;

/**
 * Estados posibles de un nodo (persona) en el modelo SIRV.
 *
 * Transiciones válidas:
 *   S → I  (contagio, durante la simulación)
 *   I → R  (recuperación, cuando diasInfectado >= diasRecuperacion)
 *   S → V  (vacunación, solo antes de iniciar la simulación)
 *
 * No existen las transiciones inversas: la inmunidad es permanente.
 */
public enum EstadoSIRV {

    /** Puede contraer la enfermedad. Estado inicial de la población no vacunada. */
    SUSCEPTIBLE,

    /** Tiene la enfermedad activa. Puede contagiar a vecinos SUSCEPTIBLE en cada turno. */
    INFECTADO,

    /** Superó la enfermedad. Inmune permanente; no contagia ni puede reinfectarse. */
    RECUPERADO,

    /** Inmune desde el inicio de la simulación. No puede infectarse nunca. */
    VACUNADO
}
