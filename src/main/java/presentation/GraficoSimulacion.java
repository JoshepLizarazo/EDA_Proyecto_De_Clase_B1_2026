package presentation;

import domain.model.Contacto;
import domain.model.Persona;
import domain.model.RedSocial;
import domain.value.EstadoSIRV;
import javax.swing.JComponent;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.swing_viewer.SwingViewer;
import org.graphstream.ui.swing_viewer.ViewPanel;
import org.graphstream.ui.view.Viewer;

/**
 * Visualización en tiempo real del grafo usando GraphStream 2.0.
 *
 * Colores por estado SIRV:
 *   SUSCEPTIBLE → Azul    #3498db
 *   INFECTADO   → Rojo    #e74c3c
 *   RECUPERADO  → Verde   #2ecc71
 *   VACUNADO    → Naranja #f39c12
 *
 * El tamaño del nodo refleja su grado (más conexiones = nodo más grande).
 * El grosor de la arista refleja su probContagio.
 *
 * Dos modos de uso:
 *   - {@link #inicializar(RedSocial)}        — abre su propia ventana (modo individual).
 *   - {@link #inicializarEmbebido(RedSocial)}— retorna un JComponent para insertarlo
 *                                             dentro de un JTabbedPane o panel propio
 *                                             (modo comparativo con tabs).
 */
public class GraficoSimulacion {

    private static final String CSS = """
            node {
                size-mode: dyn-size;
                size: 16px;
                text-size: 10;
                text-alignment: under;
                text-background-mode: rounded-box;
                text-background-color: rgba(255,255,255,190);
                text-padding: 2px;
                stroke-mode: plain;
                stroke-width: 2px;
                stroke-color: #ffffff;
                shadow-mode: gradient-radial;
                shadow-width: 4px;
                shadow-color: rgba(0,0,0,100), rgba(0,0,0,0);
                shadow-offset: 2px, -2px;
            }
            edge {
                arrow-size: 6px, 3px;
                fill-color: rgba(120,120,120,160);
                size: 1.5px;
            }
            """;

    private Graph graph;
    private Viewer viewer;
    private boolean activo = false;

    /**
     * Inicializa la ventana standalone y dibuja el grafo inicial.
     * Si GraphStream no puede abrir una ventana (headless), deshabilita la UI silenciosamente.
     */
    public void inicializar(RedSocial red) {
        try {
            System.setProperty("org.graphstream.ui", "swing");
            construirGrafo(red);
            graph.display();
            activo = true;
        } catch (Exception e) {
            System.out.println("  [Visualización no disponible: " + e.getMessage() + "]");
            activo = false;
        }
    }

    /**
     * Inicializa el grafo y retorna su vista Swing como JComponent, listo para
     * insertarse en un contenedor (ej. JTabbedPane). No abre ventana propia.
     *
     * @return el ViewPanel de GraphStream, o {@code null} si la visualización no está disponible.
     */
    public JComponent inicializarEmbebido(RedSocial red) {
        try {
            System.setProperty("org.graphstream.ui", "swing");
            construirGrafo(red);
            viewer = new SwingViewer(graph, SwingViewer.ThreadingModel.GRAPH_IN_GUI_THREAD);
            viewer.enableAutoLayout();
            ViewPanel view = (ViewPanel) viewer.addDefaultView(false);
            activo = true;
            return view;
        } catch (Exception e) {
            System.out.println("  [Visualización embebida no disponible: " + e.getMessage() + "]");
            activo = false;
            return null;
        }
    }

    /** Construye nodos y aristas del grafo a partir de la RedSocial. */
    private void construirGrafo(RedSocial red) {
        graph = new SingleGraph("Epidemia SIRV");
        graph.setAttribute("ui.stylesheet", CSS);
        graph.setAttribute("ui.quality");
        graph.setAttribute("ui.antialias");
        graph.setAttribute("layout.force", 2.2);
        graph.setAttribute("layout.quality", 4);

        // Crear nodos pre-posicionados en círculo (evita NonInvertibleTransformException)
        java.util.List<Persona> lista = new java.util.ArrayList<>(red.getTodasLasPersonas());
        int total = lista.size();
        double radio = total * 3.0;
        for (int i = 0; i < total; i++) {
            Persona p = lista.get(i);
            Node n = graph.addNode(p.getId());
            n.setAttribute("ui.label", p.getId());
            n.setAttribute("ui.style", estiloNodo(p.getEstado()));
            n.setAttribute("ui.size", 8 + red.getGrado(p) * 2);
            double angle = 2 * Math.PI * i / total;
            n.setAttribute("xyz", radio * Math.cos(angle), radio * Math.sin(angle), 0);
        }

        // Crear aristas
        for (Contacto c : red.getTodosLosContactos()) {
            String edgeId = c.getOrigen().getId() + "_" + c.getDestino().getId();
            if (graph.getEdge(edgeId) == null) {
                try {
                    org.graphstream.graph.Edge e = graph.addEdge(
                            edgeId, c.getOrigen().getId(), c.getDestino().getId(), true);
                    int grosor = Math.max(1, (int) (c.getProbContagio() * 4));
                    e.setAttribute("ui.style", "size: " + grosor + "px;");
                } catch (Exception ignored) { /* arista duplicada */ }
            }
        }
    }

    /**
     * Actualiza los colores de los nodos según su estado SIRV actual.
     */
    public void actualizarTurno(RedSocial red, int turnoActual) {
        if (!activo || graph == null) return;
        try {
            graph.setAttribute("ui.title", "Epidemia SIRV — Turno " + turnoActual);
            for (Persona p : red.getTodasLasPersonas()) {
                Node n = graph.getNode(p.getId());
                if (n != null) n.setAttribute("ui.style", estiloNodo(p.getEstado()));
            }
        } catch (Exception ignored) {}
    }

    /**
     * Actualiza el grosor de aristas tras un evento epidemiológico (reducción de pesos).
     */
    public void notificarCambioAristas(RedSocial red) {
        if (!activo || graph == null) return;
        try {
            for (Contacto c : red.getTodosLosContactos()) {
                String edgeId = c.getOrigen().getId() + "_" + c.getDestino().getId();
                org.graphstream.graph.Edge e = graph.getEdge(edgeId);
                if (e != null) {
                    int grosor = Math.max(1, (int) (c.getProbContagio() * 4));
                    e.setAttribute("ui.style", "size: " + grosor + "px;");
                }
            }
        } catch (Exception ignored) {}
    }

    public void cerrar() {
        activo = false;
    }

    public boolean isActivo() { return activo; }

    // ── Colores por estado ────────────────────────────────────────────────────

    private String estiloNodo(EstadoSIRV estado) {
        String color = switch (estado) {
            case SUSCEPTIBLE -> "#3498db";
            case INFECTADO   -> "#e74c3c";
            case RECUPERADO  -> "#2ecc71";
            case VACUNADO    -> "#f39c12";
        };
        return "fill-color: " + color + ";";
    }
}
