package presentation;

/**
 * Modo de ejecución elegido en el menú principal.
 *
 *   INDIVIDUAL  — una sola estrategia elegida por el usuario, con visualización.
 *   COMPARATIVO — las 6 estrategias sobre la MISMA red (mismos infectados), con tabs.
 *   LOTE        — N grafos distintos por estrategia; comparación promedio + acumulada.
 */
public enum ModoSimulacion {
    INDIVIDUAL,
    COMPARATIVO,
    LOTE
}
