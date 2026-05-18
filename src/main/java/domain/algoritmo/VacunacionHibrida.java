package domain.algoritmo;

import domain.model.Persona;
import domain.model.RedSocial;
import domain.value.EstadoSIRV;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vacunación híbrida por score compuesto — estrategia propia del equipo.
 *
 * Fundamento: ninguna métrica individual captura toda la complejidad del
 * riesgo de propagación. Esta estrategia combina cuatro factores en un score
 * único que balancea importancia estructural de la red con vulnerabilidad
 * epidemiológica individual:
 *
 *   score(v) = α·CB_norm(v) + β·edad_norm(v) + γ·estrato_v(v) + δ·grado_norm(v)
 *
 * Factores y su significado:
 *   α = 0.35 — betweenness normalizado: cuánto actúa v como "puente" en la red.
 *              CB_norm = CB(v) / max(CB)  → normaliza a [0, 1].
 *   β = 0.25 — riesgo por edad: a mayor edad, mayor mortalidad potencial si se infecta.
 *              edad_norm = edad / 90.0  → normaliza a [0, 1].
 *   γ = 0.25 — vulnerabilidad socioeconómica: estrato bajo → menos acceso a atención médica.
 *              estrato_v = (7 - estrato) / 6.0 → estrato 1 da 1.0; estrato 6 da ≈ 0.17.
 *   δ = 0.15 — grado normalizado: cuántas personas puede contagiar directamente.
 *              grado_norm = grado(v) / max(grado).
 *
 * Justificación de los pesos:
 *   La posición en la red (α + δ = 0.50) determina el potencial de propagación.
 *   El perfil personal (β + γ = 0.50) determina el impacto sobre el individuo.
 *   Dar igual peso a estructura de red y perfil personal produce una estrategia
 *   equilibrada entre cortar cadenas de contagio y proteger a los más vulnerables.
 *
 * Algoritmo (4 pasos):
 *   1. Calcular betweenness de todos los nodos (reutilizando VacunacionBetweenness).
 *   2. Para cada susceptible, calcular los 4 factores normalizados y el score compuesto.
 *   3. Ordenar por score descendente.
 *   4. Vacunar el 20% con mayor score.
 *
 * Complejidad: O(N × (N + M)) — dominado por el cálculo de betweenness.
 *
 * Ventaja:  la más completa conceptualmente. Protege tanto la estructura de la red
 *           (corta puentes) como a los individuos más vulnerables (mayores, estrato bajo).
 * Desventaja: igual de costosa que Betweenness puro. Los pesos α,β,γ,δ son heurísticos;
 *             un ajuste basado en datos reales del contexto colombiano podría mejorarla.
 *
 * Comparación con Comunidades:
 *   Comunidades opera a nivel de GRUPO (decide qué estrato priorizar colectivamente).
 *   Híbrida opera a nivel de INDIVIDUO (score personalizado por nodo).
 *   Son complementarias: Comunidades es más interpretable; Híbrida, más precisa.
 *
 * @see VacunacionBetweenness de donde reutiliza calcularBetweenness()
 * @see VacunacionComunidades para la estrategia complementaria a nivel de grupo
 */
public class VacunacionHibrida {

    private static final double ALPHA    = 0.35; // betweenness normalizado
    private static final double BETA     = 0.25; // riesgo por edad
    private static final double GAMMA    = 0.25; // vulnerabilidad por estrato
    private static final double DELTA    = 0.15; // grado normalizado
    private static final double PORCENTAJE = 0.20;

    /**
     * Calcula el score híbrido compuesto para un nodo con los factores ya normalizados.
     * Expuesto como método público para facilitar pruebas unitarias.
     */
    public double calcularScore(double betweennessNorm, double edadNorm,
                                double estratoVulnerabilidad, double gradoNorm) {
        return ALPHA * betweennessNorm
             + BETA  * edadNorm
             + GAMMA * estratoVulnerabilidad
             + DELTA * gradoNorm;
    }

    /**
     * Vacuna el 20% de susceptibles con mayor score híbrido.
     *
     * @param red red social sobre la que se aplica la vacunación
     * @return lista de personas que fueron vacunadas
     */
    public List<Persona> vacunar(RedSocial red) {
        List<Persona> susceptibles = new ArrayList<>(red.getPersonasPorEstado(EstadoSIRV.SUSCEPTIBLE));
        if (susceptibles.isEmpty()) return new ArrayList<>();

        // Paso 1: calcular betweenness reutilizando VacunacionBetweenness
        VacunacionBetweenness bc = new VacunacionBetweenness();
        Map<Persona, Double> cbRaw = bc.calcularBetweenness(red);

        // Máximos para normalizar betweenness y grado a [0, 1]
        double maxCB   = cbRaw.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        int maxGrado   = susceptibles.stream().mapToInt(p -> red.getGrado(p)).max().orElse(1);

        // Paso 2: calcular score compuesto para cada susceptible
        Map<Persona, Double> scores = new HashMap<>();
        for (Persona p : susceptibles) {
            double cbNorm    = maxCB   > 0 ? cbRaw.getOrDefault(p, 0.0) / maxCB : 0.0;
            double edadNorm  = p.getEdad() / 90.0;
            // Estrato 1 es el más vulnerable (estratoV = 1.0); estrato 6 el menos (≈ 0.17)
            double estratoV  = (7.0 - p.getEstrato()) / 6.0;
            double gradoNorm = maxGrado > 0 ? (double) red.getGrado(p) / maxGrado : 0.0;
            scores.put(p, calcularScore(cbNorm, edadNorm, estratoV, gradoNorm));
        }

        // Paso 3-4: ordenar por score descendente y vacunar el 20%
        susceptibles.sort((a, b) -> Double.compare(scores.get(b), scores.get(a)));

        int cuota = (int) Math.floor(PORCENTAJE * susceptibles.size());
        List<Persona> vacunados = new ArrayList<>();
        for (int i = 0; i < cuota; i++) {
            susceptibles.get(i).setEstado(EstadoSIRV.VACUNADO);
            vacunados.add(susceptibles.get(i));
        }
        return vacunados;
    }
}
