package domain.model;

import domain.value.EstadoSIRV;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Grafo dirigido y ponderado que representa la red social.
 *
 * Estructura interna: {@code HashMap<Persona, List<Contacto>>} (listas de adyacencia).
 * Cada entrada del mapa es un nodo y su lista de aristas salientes.
 *
 * Convención de dirección:
 *   agregarContacto(A, B, p) → A puede contagiar a B.
 *   Para que B pueda contagiar a A, se debe agregar la arista inversa explícitamente.
 *
 * Los algoritmos de vacunación y el ModeloSIRV operan directamente sobre esta clase.
 */
public class RedSocial {

    // Estructura de adyacencia: nodo → lista de aristas salientes
    private final Map<Persona, List<Contacto>> adyacencia;

    public RedSocial() {
        this.adyacencia = new HashMap<>();
    }

    // ── Operaciones sobre nodos ────────────────────────────────────────────────

    /** Agrega un nodo al grafo. Si ya existe, no hace nada (idempotente). */
    public void agregarPersona(Persona persona) {
        adyacencia.putIfAbsent(persona, new ArrayList<>());
    }

    /** Retorna todos los nodos del grafo (vista no modificable). */
    public Collection<Persona> getTodasLasPersonas() {
        return Collections.unmodifiableSet(adyacencia.keySet());
    }

    /** Retorna todos los nodos que están en el estado indicado. */
    public List<Persona> getPersonasPorEstado(EstadoSIRV estado) {
        return adyacencia.keySet().stream()
                .filter(p -> p.getEstado() == estado)
                .collect(Collectors.toList());
    }

    /** Número total de nodos en el grafo. */
    public int getTotalPersonas() {
        return adyacencia.size();
    }

    // ── Operaciones sobre aristas ──────────────────────────────────────────────

    /**
     * Agrega una arista dirigida: origen puede contagiar a destino con la probabilidad indicada.
     * Ambos nodos deben existir previamente en el grafo.
     *
     * @throws IllegalArgumentException si alguno de los nodos no existe en la red.
     */
    public void agregarContacto(Persona origen, Persona destino, double probContagio) {
        if (!adyacencia.containsKey(origen) || !adyacencia.containsKey(destino)) {
            throw new IllegalArgumentException(
                    "Ambas personas deben existir en la red antes de agregar un contacto.");
        }
        adyacencia.get(origen).add(new Contacto(origen, destino, probContagio));
    }

    /** Retorna las aristas salientes de una persona. Lista vacía si la persona no existe. */
    public List<Contacto> getContactos(Persona persona) {
        return adyacencia.getOrDefault(persona, Collections.emptyList());
    }

    /** Retorna todas las aristas del grafo (para que GestorEventos pueda modificarlas). */
    public List<Contacto> getTodosLosContactos() {
        return adyacencia.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /** Grado saliente: número de aristas que salen del nodo. Usado por VacunacionHubs. */
    public int getGrado(Persona persona) {
        return adyacencia.getOrDefault(persona, Collections.emptyList()).size();
    }

    /** Número total de aristas en el grafo. */
    public int getTotalContactos() {
        return adyacencia.values().stream().mapToInt(List::size).sum();
    }

    // ── Inicialización de la simulación ───────────────────────────────────────

    /**
     * Infecta {@code cantidad} personas aleatorias SUSCEPTIBLE para iniciar el brote.
     * Llamado por IniciarSimulacionCommand antes de correr ModeloSIRV.
     *
     * @param cantidad número de pacientes cero
     * @param random   semilla controlada para reproducibilidad de experimentos
     */
    public void setearPacienteCero(int cantidad, Random random) {
        List<Persona> susceptibles = getPersonasPorEstado(EstadoSIRV.SUSCEPTIBLE);
        Collections.shuffle(susceptibles, random);
        int aInfectar = Math.min(cantidad, susceptibles.size());
        for (int i = 0; i < aInfectar; i++) {
            susceptibles.get(i).setEstado(EstadoSIRV.INFECTADO);
        }
    }

    @Override
    public String toString() {
        return String.format("RedSocial[nodos=%d | aristas=%d]",
                getTotalPersonas(), getTotalContactos());
    }
}
