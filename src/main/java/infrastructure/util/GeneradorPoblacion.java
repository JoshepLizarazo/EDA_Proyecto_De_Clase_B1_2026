package infrastructure.util;

import domain.model.Contacto;
import domain.model.Persona;
import domain.model.RedSocial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

/**
 * Genera una RedSocial con distribuciones demográficas colombianas.
 * Cinco fases: personas → clusters familiares → vecindarios → hubs comunitarios → pesos.
 *
 * Las personas se identifican únicamente por id (P001, P002, ...). No se asigna nombre.
 */
public class GeneradorPoblacion {

    private static final double PROB_BASE_DEFAULT = 0.20;

    private static final String[] OCUPACIONES = {
        "estudiante", "informal", "empleado", "salud", "jubilado"
    };
    // Pesos acumulados: 35%, 25%, 25%, 10%, 5%
    private static final double[] OCUP_ACUM = {0.35, 0.60, 0.85, 0.95, 1.00};

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Genera una RedSocial completa con {@code tamano} personas.
     */
    public RedSocial generar(int tamano, long semilla) {
        Random rand = new Random(semilla);
        RedSocial red = new RedSocial();
        List<Persona> personas = new ArrayList<>();

        // Fase 1: generar N personas
        for (int i = 0; i < tamano; i++) {
            Persona p = generarPersona(String.format("P%03d", i + 1), rand);
            red.agregarPersona(p);
            personas.add(p);
        }

        // Fase 2: clusters familiares (4–7 personas por familia)
        List<List<Persona>> familias = formarFamilias(personas, rand);
        for (List<Persona> familia : familias) {
            conectarFamilia(familia, red, rand);
        }

        // Fase 3: vecindarios (familias del mismo estrato conectadas moderadamente)
        conectarVecindarios(familias, red, rand);

        // Fase 4: hubs comunitarios (mercado, iglesia, transporte)
        crearHubsComunitarios(personas, red, rand);

        // Fase 5: garantizar un único componente conexo
        garantizarConectividad(personas, red, rand);

        return red;
    }

    // ── Cálculo de peso de arista ─────────────────────────────────────────────

    /**
     * probContagio = probBase × fEdad(origen) × fEstrato(origen) × fOcupacion(origen)
     * Normalizado a [0.05, 0.95].
     */
    public static double calcularProbContagio(Persona origen, double probBase) {
        double peso = probBase
                * origen.factorEdad()
                * origen.factorEstrato()
                * origen.factorOcupacion();
        return Math.max(0.05, Math.min(0.95, peso));
    }

    public static double calcularProbContagio(Persona origen) {
        return calcularProbContagio(origen, PROB_BASE_DEFAULT);
    }

    // ── Generación de personas ────────────────────────────────────────────────

    private Persona generarPersona(String id, Random rand) {
        int edad    = sortearEdad(rand);
        int estrato = sortearEstrato(rand);
        String ocup = sortearOcupacion(rand, edad);
        return new Persona(id, edad, estrato, ocup);
    }

    /** Distribución DANE: 0-14 (24%), 15-35 (38%), 36-59 (26%), 60-90 (12%). */
    private int sortearEdad(Random rand) {
        double r = rand.nextDouble();
        if (r < 0.24) return rand.nextInt(15);
        if (r < 0.62) return 15 + rand.nextInt(21);
        if (r < 0.88) return 36 + rand.nextInt(24);
        return 60 + rand.nextInt(31);
    }

    /** Estratos: 1 (30%), 2 (30%), 3 (30%), 4 (5%), 5 (3%), 6 (2%). */
    private int sortearEstrato(Random rand) {
        double r = rand.nextDouble();
        if (r < 0.30) return 1;
        if (r < 0.60) return 2;
        if (r < 0.90) return 3;
        if (r < 0.95) return 4;
        if (r < 0.98) return 5;
        return 6;
    }

    /** Ocupación con correlación de edad: mayores → jubilado; jóvenes → estudiante. */
    private String sortearOcupacion(Random rand, int edad) {
        if (edad >= 65) return "jubilado";
        if (edad <= 18) return "estudiante";
        double r = rand.nextDouble();
        for (int i = 0; i < OCUP_ACUM.length; i++) {
            if (r < OCUP_ACUM[i]) return OCUPACIONES[i];
        }
        return "empleado";
    }

    // ── Clusters familiares ───────────────────────────────────────────────────

    private List<List<Persona>> formarFamilias(List<Persona> personas, Random rand) {
        List<Persona> shuffled = new ArrayList<>(personas);
        Collections.shuffle(shuffled, rand);

        List<List<Persona>> familias = new ArrayList<>();
        int idx = 0;
        while (idx < shuffled.size()) {
            int tamFamilia = 4 + rand.nextInt(4); // 4–7
            int fin = Math.min(idx + tamFamilia, shuffled.size());
            familias.add(new ArrayList<>(shuffled.subList(idx, fin)));
            idx = fin;
        }
        return familias;
    }

    /**
     * Conecta todos los miembros de la familia entre sí bidireccionalmente
     * con probBase alta (0.30–0.40 — convivencia diaria).
     */
    private void conectarFamilia(List<Persona> familia, RedSocial red, Random rand) {
        double probBase = 0.30 + rand.nextDouble() * 0.10; // 0.30 – 0.40
        for (int i = 0; i < familia.size(); i++) {
            for (int j = 0; j < familia.size(); j++) {
                if (i == j) continue;
                Persona u = familia.get(i);
                Persona v = familia.get(j);
                red.agregarContacto(u, v, calcularProbContagio(u, probBase));
            }
        }
    }

    // ── Vecindarios ───────────────────────────────────────────────────────────

    /**
     * Agrupa familias por estrato similar y las conecta moderadamente (0.10–0.18).
     * Simula el contacto vecinal dentro del mismo barrio.
     */
    private void conectarVecindarios(List<List<Persona>> familias, RedSocial red, Random rand) {
        for (int i = 0; i < familias.size() - 1; i++) {
            List<Persona> fa = familias.get(i);
            List<Persona> fb = familias.get(i + 1);
            int estratoA = fa.get(0).getEstrato();
            int estratoB = fb.get(0).getEstrato();

            // Solo conectar si los estratos son similares (diferencia ≤ 1)
            if (Math.abs(estratoA - estratoB) > 1) continue;

            double probBase = 0.10 + rand.nextDouble() * 0.08; // 0.10 – 0.18
            int conexiones = 1 + rand.nextInt(Math.min(3, Math.min(fa.size(), fb.size())));
            for (int k = 0; k < conexiones; k++) {
                Persona u = fa.get(rand.nextInt(fa.size()));
                Persona v = fb.get(rand.nextInt(fb.size()));
                red.agregarContacto(u, v, calcularProbContagio(u, probBase));
                red.agregarContacto(v, u, calcularProbContagio(v, probBase));
            }
        }
    }

    // ── Conectividad global ───────────────────────────────────────────────────

    /**
     * Detecta componentes conexos (tratando el grafo como no dirigido) y une cada
     * componente menor al mayor con una arista bidireccional de probabilidad baja,
     * garantizando que el grafo sea completamente conexo.
     */
    private void garantizarConectividad(List<Persona> personas, RedSocial red, Random rand) {
        if (personas.size() < 2) return;

        Map<Persona, Set<Persona>> ady = new HashMap<>();
        for (Persona p : personas) ady.put(p, new HashSet<>());
        for (Persona p : personas) {
            for (Contacto c : red.getContactos(p)) {
                ady.get(p).add(c.getDestino());
                ady.get(c.getDestino()).add(p);
            }
        }

        List<List<Persona>> componentes = new ArrayList<>();
        Set<Persona> noVisitados = new HashSet<>(personas);
        while (!noVisitados.isEmpty()) {
            List<Persona> comp = new ArrayList<>();
            Queue<Persona> cola = new LinkedList<>();
            Persona inicio = noVisitados.iterator().next();
            cola.add(inicio);
            noVisitados.remove(inicio);
            while (!cola.isEmpty()) {
                Persona curr = cola.poll();
                comp.add(curr);
                for (Persona vecino : ady.get(curr)) {
                    if (noVisitados.remove(vecino)) cola.add(vecino);
                }
            }
            componentes.add(comp);
        }

        if (componentes.size() == 1) return;

        componentes.sort((a, b) -> b.size() - a.size());

        List<Persona> principal = new ArrayList<>(componentes.get(0));
        for (int c = 1; c < componentes.size(); c++) {
            Persona origen  = componentes.get(c).get(rand.nextInt(componentes.get(c).size()));
            Persona destino = principal.get(rand.nextInt(principal.size()));
            double prob     = 0.10 + rand.nextDouble() * 0.10;
            red.agregarContacto(origen, destino, prob);
            red.agregarContacto(destino, origen, prob);
            principal.addAll(componentes.get(c));
        }
    }

    // ── Hubs comunitarios ─────────────────────────────────────────────────────

    /**
     * Crea 2–3 hubs comunitarios (mercado, iglesia, transporte público).
     * Cada hub conecta personas de distintos clusters con probBase moderada (0.08–0.15).
     */
    private void crearHubsComunitarios(List<Persona> personas, RedSocial red, Random rand) {
        int numHubs = 2 + rand.nextInt(2); // 2 o 3 hubs
        int conexionesPorHub = Math.max(5, personas.size() / 10);

        for (int h = 0; h < numHubs; h++) {
            Persona hub = personas.get(rand.nextInt(personas.size()));
            double probBase = 0.08 + rand.nextDouble() * 0.07;

            List<Persona> shuffled = new ArrayList<>(personas);
            Collections.shuffle(shuffled, rand);
            int cnt = 0;
            for (Persona p : shuffled) {
                if (p.equals(hub) || cnt >= conexionesPorHub) break;
                red.agregarContacto(hub, p, calcularProbContagio(hub, probBase));
                red.agregarContacto(p, hub, calcularProbContagio(p, probBase));
                cnt++;
            }
        }
    }
}
