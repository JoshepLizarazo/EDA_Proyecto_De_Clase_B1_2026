package presentation;

import domain.model.Contacto;
import domain.model.Persona;
import domain.model.RedSocial;
import domain.value.EstadoSIRV;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
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

    // Colores del banner de alertas por tipo de evento
    private static final Color COLOR_ALERTA_LEVE  = new Color(230, 150, 20);   // naranja
    private static final Color COLOR_CUARENTENA   = new Color(200, 80,  20);   // naranja oscuro
    private static final Color COLOR_LOCKDOWN     = new Color(170, 25,  25);   // rojo oscuro
    private static final Color COLOR_FATIGA       = new Color(30,  130, 80);   // verde
    private static final Color COLOR_FATIGA_FIN   = new Color(160, 110, 20);   // dorado
    private static final Color COLOR_FONDO_BARRA  = new Color(33,  37,  43);   // gris oscuro (estado normal)

    private Graph graph;
    private Viewer viewer;
    private boolean activo = false;
    private int totalNodos = 0;
    private JLabel etiquetaTurno;
    private JLabel etiquetaAlerta;
    private Timer timerAlerta;

    /**
     * Inicializa la ventana standalone y dibuja el grafo inicial, con el contador
     * de turno al pie. Si GraphStream no puede abrir una ventana (headless),
     * deshabilita la UI silenciosamente.
     */
    public void inicializar(RedSocial red) {
        JComponent panel = inicializarEmbebido(red);
        if (panel == null) return;
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Epidemia SIRV");
            f.getContentPane().setLayout(new BorderLayout());
            f.getContentPane().add(panel, BorderLayout.CENTER);
            f.setPreferredSize(new Dimension(920, 720));
            f.pack();
            f.setLocationRelativeTo(null);
            f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            f.setVisible(true);
        });
    }

    /**
     * Inicializa el grafo y retorna un JComponent con la vista de GraphStream
     * (centro) y una etiqueta de turno al pie (sur), listo para insertarse en un
     * contenedor (ej. JTabbedPane) o en la ventana standalone.
     *
     * @return el panel envuelto, o {@code null} si la visualización no está disponible.
     */
    public JComponent inicializarEmbebido(RedSocial red) {
        try {
            System.setProperty("org.graphstream.ui", "swing");
            construirGrafo(red);
            viewer = new SwingViewer(graph, SwingViewer.ThreadingModel.GRAPH_IN_GUI_THREAD);
            // Auto-layout es O(n²) — solo activarlo en redes pequeñas
            if (totalNodos <= 100) viewer.enableAutoLayout();
            ViewPanel view = (ViewPanel) viewer.addDefaultView(false);
            activo = true;
            return envolverConEtiqueta(view);
        } catch (Exception e) {
            System.out.println("  [Visualización embebida no disponible: " + e.getMessage() + "]");
            activo = false;
            return null;
        }
    }

    /** Envuelve la vista del grafo con barra superior de alertas y barra inferior de turno. */
    private JComponent envolverConEtiqueta(JComponent vista) {
        // ── Barra inferior: turno + conteo SIRV ──────────────────────────────
        etiquetaTurno = new JLabel("Turno 0", SwingConstants.CENTER);
        etiquetaTurno.setOpaque(true);
        etiquetaTurno.setBackground(COLOR_FONDO_BARRA);
        etiquetaTurno.setForeground(new Color(232, 234, 238));
        etiquetaTurno.setFont(etiquetaTurno.getFont().deriveFont(Font.BOLD, 13f));
        etiquetaTurno.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));

        // ── Barra superior: banner de alertas ────────────────────────────────
        etiquetaAlerta = new JLabel(" ", SwingConstants.CENTER);
        etiquetaAlerta.setOpaque(true);
        etiquetaAlerta.setBackground(COLOR_FONDO_BARRA);
        etiquetaAlerta.setForeground(new Color(232, 234, 238));
        etiquetaAlerta.setFont(etiquetaAlerta.getFont().deriveFont(Font.BOLD, 13f));
        etiquetaAlerta.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
        etiquetaAlerta.setVisible(false);  // oculta hasta que llegue la primera alerta

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(etiquetaAlerta, BorderLayout.NORTH);
        panel.add(vista,          BorderLayout.CENTER);
        panel.add(etiquetaTurno,  BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Muestra un mensaje de alerta en el banner superior durante 4 segundos,
     * luego lo oculta automáticamente.
     *
     * Llamado desde SimulacionService cuando se dispara un evento NPI o cambia la fatiga.
     *
     * @param texto   texto del mensaje
     * @param fondo   color de fondo del banner
     * @param textColor color del texto
     */
    public void mostrarAlerta(String texto, Color fondo, Color textColor) {
        if (!activo || etiquetaAlerta == null) return;
        SwingUtilities.invokeLater(() -> {
            // Cancelar cualquier timer anterior para no ocultar la nueva alerta antes de tiempo
            if (timerAlerta != null && timerAlerta.isRunning()) timerAlerta.stop();

            etiquetaAlerta.setText(texto);
            etiquetaAlerta.setBackground(fondo);
            etiquetaAlerta.setForeground(textColor);
            etiquetaAlerta.setVisible(true);

            // Auto-ocultar después de 4 segundos
            timerAlerta = new Timer(4000, e -> {
                etiquetaAlerta.setVisible(false);
                ((Timer) e.getSource()).stop();
            });
            timerAlerta.setRepeats(false);
            timerAlerta.start();
        });
    }

    /** Atajos de color para cada tipo de evento predefinido. */
    public void alertaEventoNPI(String nombreEvento) {
        Color fondo = switch (nombreEvento.toLowerCase()) {
            case "alerta leve" -> COLOR_ALERTA_LEVE;
            case "cuarentena"  -> COLOR_CUARENTENA;
            case "lockdown"    -> COLOR_LOCKDOWN;
            default            -> COLOR_CUARENTENA;
        };
        mostrarAlerta("⚠  " + nombreEvento.toUpperCase() + " — Medidas de bioseguridad activadas",
                fondo, Color.WHITE);
    }

    public void alertaFatigaIniciada() {
        mostrarAlerta("↗  FATIGA SOCIAL — Epidemia en descenso: la población se relaja",
                COLOR_FATIGA, Color.WHITE);
    }

    public void alertaFatigaReiniciada() {
        mostrarAlerta("↑  NUEVA ALZA — Precaución restaurada por rebote de infectados",
                COLOR_FATIGA_FIN, Color.WHITE);
    }

    /** Construye nodos y aristas del grafo a partir de la RedSocial. */
    private void construirGrafo(RedSocial red) {
        graph = new SingleGraph("Epidemia SIRV");
        graph.setAttribute("ui.stylesheet", CSS);
        graph.setAttribute("ui.quality");
        graph.setAttribute("ui.antialias");

        java.util.List<Persona> lista = new java.util.ArrayList<>(red.getTodasLasPersonas());
        int total = lista.size();
        totalNodos = total;
        boolean esGrande = total > 100;

        if (!esGrande) {
            graph.setAttribute("layout.force", 0.8);
            graph.setAttribute("layout.quality", 2);
        }

        // Radio proporcional a √n para que la densidad visual sea constante
        double radio = Math.max(20, Math.sqrt(total) * 8);
        int tamBase   = esGrande ? 5 : 8;
        int tamMax    = esGrande ? 18 : 40;
        for (int i = 0; i < total; i++) {
            Persona p = lista.get(i);
            Node n = graph.addNode(p.getId());
            if (!esGrande) n.setAttribute("ui.label", p.getId());
            n.setAttribute("ui.style", estiloNodo(p.getEstado()));
            n.setAttribute("ui.size", Math.min(tamMax, tamBase + red.getGrado(p)));
            double angle = 2 * Math.PI * i / total;
            n.setAttribute("xyz", radio * Math.cos(angle), radio * Math.sin(angle), 0);
        }

        // Para redes grandes solo dibujar conexiones fuertes (familia y hubs).
        // El umbral 0.15 retiene ~familias (0.30-0.40) y hubs relevantes, eliminando
        // el ruido visual de conexiones de largo alcance débiles.
        double umbralArista = esGrande ? 0.15 : 0.0;
        int alphaArista     = esGrande ? 90 : 160;

        for (Contacto c : red.getTodosLosContactos()) {
            if (c.getProbContagio() < umbralArista) continue;
            String edgeId = c.getOrigen().getId() + "_" + c.getDestino().getId();
            if (graph.getEdge(edgeId) == null) {
                try {
                    org.graphstream.graph.Edge e = graph.addEdge(
                            edgeId, c.getOrigen().getId(), c.getDestino().getId(), true);
                    int grosor = Math.max(1, (int) (c.getProbContagio() * 4));
                    e.setAttribute("ui.style",
                            "size: " + grosor + "px; fill-color: rgba(120,120,120," + alphaArista + ");");
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
            actualizarEtiquetaTurno(red, turnoActual);
        } catch (Exception ignored) {}
    }

    /** Refresca la barra inferior con el turno y el conteo de cada estado SIRV. */
    private void actualizarEtiquetaTurno(RedSocial red, int turno) {
        if (etiquetaTurno == null) return;
        int s = red.getPersonasPorEstado(EstadoSIRV.SUSCEPTIBLE).size();
        int i = red.getPersonasPorEstado(EstadoSIRV.INFECTADO).size();
        int r = red.getPersonasPorEstado(EstadoSIRV.RECUPERADO).size();
        int v = red.getPersonasPorEstado(EstadoSIRV.VACUNADO).size();
        String texto = String.format(
                "Turno %d      S: %d      I: %d      R: %d      V: %d", turno, s, i, r, v);
        SwingUtilities.invokeLater(() -> etiquetaTurno.setText(texto));
    }

    /**
     * Actualiza el grosor de aristas tras un evento epidemiológico (reducción de pesos).
     */
    public void notificarCambioAristas(RedSocial red) {
        if (!activo || graph == null) return;
        boolean esGrande = totalNodos > 100;
        int alphaArista  = esGrande ? 90 : 160;
        try {
            for (Contacto c : red.getTodosLosContactos()) {
                String edgeId = c.getOrigen().getId() + "_" + c.getDestino().getId();
                org.graphstream.graph.Edge e = graph.getEdge(edgeId);
                if (e != null) {
                    int grosor = Math.max(1, (int) (c.getProbContagio() * 4));
                    e.setAttribute("ui.style",
                            "size: " + grosor + "px; fill-color: rgba(120,120,120," + alphaArista + ");");
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
