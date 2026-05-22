package infrastructure.persistence;

import domain.model.RedSocial;

/**
 * Repositorio en memoria que mantiene la RedSocial activa durante la ejecución.
 *
 * Permite que distintos servicios (SimulacionService, VacunacionService, GestorEventos)
 * accedan al mismo grafo sin pasarlo como parámetro en cada llamada.
 *
 * Patrón: repositorio singleton de sesión — existe una sola instancia de RedSocial
 * activa a la vez. Al iniciar una nueva simulación se reemplaza la instancia anterior.
 */
public class RedMemoryRepository {

    private RedSocial redActiva;

    // TODO: constructor

    /** Guarda una nueva RedSocial como la activa para la simulación actual. */
    public void guardar(RedSocial red) {
        this.redActiva = red;
    }

    /** Retorna la RedSocial activa. Null si aún no se ha inicializado. */
    public RedSocial obtener() {
        return redActiva;
    }

    /** Elimina la referencia a la red activa (limpieza entre simulaciones). */
    public void limpiar() {
        this.redActiva = null;
    }
}
