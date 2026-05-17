package application.service;

import domain.algoritmo.VacunacionAleatoria;
import domain.algoritmo.VacunacionBFSPonderado;
import domain.algoritmo.VacunacionBetweenness;
import domain.algoritmo.VacunacionComunidades;
import domain.algoritmo.VacunacionHibrida;
import domain.algoritmo.VacunacionHubs;
import domain.model.Persona;
import domain.model.RedSocial;
import domain.value.EstrategiaVacunacion;
import java.util.List;

/**
 * Delega la vacunación al algoritmo correspondiente según la estrategia elegida.
 *
 *   ALEATORIA     → VacunacionAleatoria     (O(N))
 *   HUBS          → VacunacionHubs          (O(N log N))
 *   BETWEENNESS   → VacunacionBetweenness   (O(N×(N+M)))
 *   COMUNIDADES   → VacunacionComunidades   (O(N+M))
 *   HIBRIDA       → VacunacionHibrida       (O(N×(N+M)))
 *   BFS_PONDERADO → VacunacionBFSPonderado  (O(K×N×(N+M) log N))
 */
public class VacunacionService {

    private final VacunacionAleatoria aleatoria;
    private final VacunacionHubs hubs;
    private final VacunacionBetweenness betweenness;
    private final VacunacionComunidades comunidades;
    private final VacunacionHibrida hibrida;
    private final VacunacionBFSPonderado bfsPonderado;

    public VacunacionService(long semilla) {
        this.aleatoria    = new VacunacionAleatoria(semilla);
        this.hubs         = new VacunacionHubs();
        this.betweenness  = new VacunacionBetweenness();
        this.comunidades  = new VacunacionComunidades();
        this.hibrida      = new VacunacionHibrida();
        this.bfsPonderado = new VacunacionBFSPonderado();
    }

    /**
     * Aplica la estrategia sobre la red y retorna las personas vacunadas.
     */
    public List<Persona> vacunar(RedSocial red, EstrategiaVacunacion estrategia) {
        return switch (estrategia) {
            case ALEATORIA     -> aleatoria.vacunar(red);
            case HUBS          -> hubs.vacunar(red);
            case BETWEENNESS   -> betweenness.vacunar(red);
            case COMUNIDADES   -> comunidades.vacunar(red);
            case HIBRIDA       -> hibrida.vacunar(red);
            case BFS_PONDERADO -> bfsPonderado.vacunar(red);
        };
    }
}
