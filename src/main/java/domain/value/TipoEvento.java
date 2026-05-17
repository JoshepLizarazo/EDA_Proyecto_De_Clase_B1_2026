package domain.value;

/**
 * Tipos de eventos epidemiológicos que se disparan automáticamente cuando el
 * porcentaje de infectados supera el umbral definido en EventoEpidemiologico.
 *
 * Cada tipo se dispara una sola vez por simulación.
 * GestorEventos verifica en cada turno si se cruzó algún umbral pendiente.
 *
 * Umbrales y factores:
 *   ALERTA_LEVE  → 30% infectados | factor × 0.80 (–20% contagio)
 *   CUARENTENA   → 50% infectados | factor × 0.50 (–50% contagio)
 *   LOCKDOWN     → 70% infectados | factor × 0.20 (–80% contagio)
 */
public enum TipoEvento {

    /** Distanciamiento social voluntario. Reduce contagio en 20%. */
    ALERTA_LEVE,

    /** Restricción de movilidad obligatoria. Reduce contagio en 50%. */
    CUARENTENA,

    /** Confinamiento total. Reduce contagio en 80%. */
    LOCKDOWN
}
