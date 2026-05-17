package domain.algoritmo;

import domain.model.Persona;
import domain.model.RedSocial;
import domain.value.EstadoSIRV;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vacunación híbrida por score compuesto — aporte propio del equipo. O(N×(N+M)).
 *
 * score(v) = α·CB_norm(v) + β·riesgo_edad(v) + γ·vulnerabilidad_estrato(v) + δ·deg_norm(v)
 */
public class VacunacionHibrida {

    private static final double ALPHA    = 0.35; // betweenness normalizado
    private static final double BETA     = 0.25; // riesgo por edad
    private static final double GAMMA    = 0.25; // vulnerabilidad por estrato
    private static final double DELTA    = 0.15; // grado normalizado
    private static final double PORCENTAJE = 0.20;

    public double calcularScore(double betweennessNorm, double edadNorm,
                                double estratoVulnerabilidad, double gradoNorm) {
        return ALPHA * betweennessNorm
             + BETA  * edadNorm
             + GAMMA * estratoVulnerabilidad
             + DELTA * gradoNorm;
    }

    public List<Persona> vacunar(RedSocial red) {
        List<Persona> susceptibles = new ArrayList<>(red.getPersonasPorEstado(EstadoSIRV.SUSCEPTIBLE));
        if (susceptibles.isEmpty()) return new ArrayList<>();

        // Calcular betweenness reutilizando VacunacionBetweenness
        VacunacionBetweenness bc = new VacunacionBetweenness();
        Map<Persona, Double> cbRaw = bc.calcularBetweenness(red);

        double maxCB   = cbRaw.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        int maxGrado   = susceptibles.stream().mapToInt(p -> red.getGrado(p)).max().orElse(1);

        // Calcular score compuesto para cada susceptible
        Map<Persona, Double> scores = new HashMap<>();
        for (Persona p : susceptibles) {
            double cbNorm    = maxCB   > 0 ? cbRaw.getOrDefault(p, 0.0) / maxCB : 0.0;
            double edadNorm  = p.getEdad() / 90.0;
            double estratoV  = (7.0 - p.getEstrato()) / 6.0;
            double gradoNorm = maxGrado > 0 ? (double) red.getGrado(p) / maxGrado : 0.0;
            scores.put(p, calcularScore(cbNorm, edadNorm, estratoV, gradoNorm));
        }

        // Ordenar por score descendente y vacunar el 20%
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
