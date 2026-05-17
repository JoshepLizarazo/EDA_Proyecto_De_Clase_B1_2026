package domain.value;

/**
 * Estrategias de vacunación disponibles en el simulador.
 *
 * Todas vacunan exactamente el 20% de la población susceptible
 * antes de iniciar la simulación (pre-simulación, no durante).
 *
 * VacunacionService recibe este enum y delega al algoritmo correspondiente:
 *   ALEATORIA     → VacunacionAleatoria      — O(N)
 *   HUBS          → VacunacionHubs           — O(N log N)
 *   BETWEENNESS   → VacunacionBetweenness    — O(N × (N+M))
 *   COMUNIDADES   → VacunacionComunidades    — O(N + M)
 *   HIBRIDA       → VacunacionHibrida        — O(N × (N+M))  [aporte propio]
 *   BFS_PONDERADO → VacunacionBFSPonderado   — O(K × N × (N+M) log N)
 */
public enum EstrategiaVacunacion {

    /** Selección aleatoria del 20%. Línea base de comparación. */
    ALEATORIA,

    /** Vacuna los nodos con mayor número de conexiones (hubs). */
    HUBS,

    /** Vacuna los nodos con mayor centralidad de intermediación (puentes entre comunidades). */
    BETWEENNESS,

    /**
     * Prioriza comunidades (estratos) con mayor densidad interna de aristas.
     * Dentro de cada comunidad vacuna primero a los más conectados.
     * Opera a nivel de GRUPO, no de individuo.
     */
    COMUNIDADES,

    /**
     * Score compuesto: α·betweenness + β·riesgo_edad + γ·vulnerabilidad_estrato + δ·grado.
     * Ponderación sugerida: α=0.35, β=0.25, γ=0.25, δ=0.15.
     * Opera a nivel de INDIVIDUO. Aporte propio del equipo.
     */
    HIBRIDA,

    /**
     * Vacuna los nodos que aparecen con mayor probabilidad acumulada en los caminos
     * de máximo contagio desde candidatos a paciente cero (hubs).
     * Reusa BFSPonderado como criterio de importancia estructural.
     */
    BFS_PONDERADO
}
