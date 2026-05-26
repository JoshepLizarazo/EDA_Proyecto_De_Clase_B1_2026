package presentation;

import domain.model.Contacto;
import domain.model.Persona;
import domain.model.RedSocial;
import infrastructure.util.GeneradorPoblacion;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.HashSet;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.swing_viewer.SwingViewer;
import org.graphstream.ui.swing_viewer.ViewPanel;

/**
 * Abre una ventana GraphStream y construye la red fase a fase:
 *   Fase 1 – nodos (azul)
 *   Fase 2 – clusters familiares (rojo)
 *   Fase 3 – vecindarios (naranja)
 *   Fase 4 – hubs comunitarios (morado)
 *   Fase 5 – conexiones long-range (gris)
 *   Fase 6 – aristas puente de conectividad (verde)
 *
 * Cada fase se muestra con una pausa configurable para que sea legible.
 * Los nodos crecen conforme acumulan conexiones.
 */
public class VisualizadorConstruccionRed {

    private static final String CSS = """
            node {
                size-mode: dyn-size;
                size: 10px;
                text-size: 9;
                text-alignment: under;
                text-background-mode: rounded-box;
                text-background-color: rgba(255,255,255,170);
                text-padding: 2px;
                stroke-mode: plain;
                stroke-width: 1px;
                stroke-color: #aaaaaa;
                fill-color: #3498db;
                shadow-mode: gradient-radial;
                shadow-width: 3px;
                shadow-color: rgba(0,0,0,80), rgba(0,0,0,0);
                shadow-offset: 1px, -1px;
            }
            edge {
                arrow-size: 4px, 2px;
                size: 1.5px;
            }
            """;

    // Colores RGBA por fase (coinciden con la leyenda superior)
    private static final String[] COLORES_FASE = {
        "",                           // índice 0 no usado
        "rgba(52,152,219,60)",        // fase 1 – nodos (sin aristas todavía)
        "rgba(231,76,60,210)",        // fase 2 – familia
        "rgba(230,126,34,190)",       // fase 3 – vecindario
        "rgba(155,89,182,210)",       // fase 4 – hubs
        "rgba(149,165,166,150)",      // fase 5 – long-range
        "rgba(46,204,113,220)",       // fase 6 – puente
    };

    private Graph graph;
    private JLabel etiquetaEstado;
    private final Set<String> aristasRenderizadas = new HashSet<>();
    private int nodeCounter = 0;
    private int totalNodosEsperados;

    /**
     * Construye y muestra la red paso a paso.
     * Bloquea el hilo llamante hasta que termina la generación
     * (la ventana queda abierta y el usuario la cierra cuando quiera).
     */
    public void visualizar(int tamanoRed, long semilla, int pausaMs) {
        try {
            System.setProperty("org.graphstream.ui", "swing");
            totalNodosEsperados = tamanoRed;

            graph = new SingleGraph("Construcción Red Social");
            graph.setAttribute("ui.stylesheet", CSS);
            graph.setAttribute("ui.quality");
            graph.setAttribute("ui.antialias");

            boolean esGrande = tamanoRed > 100;
            SwingViewer viewer = new SwingViewer(graph, SwingViewer.ThreadingModel.GRAPH_IN_GUI_THREAD);
            if (!esGrande) viewer.enableAutoLayout();
            ViewPanel viewPanel = (ViewPanel) viewer.addDefaultView(false);

            etiquetaEstado = crearBarraEstado("Preparando construcción...");
            JLabel leyenda  = crearLeyenda();

            JPanel contenedor = new JPanel(new BorderLayout());
            contenedor.add(leyenda,        BorderLayout.NORTH);
            contenedor.add(viewPanel,      BorderLayout.CENTER);
            contenedor.add(etiquetaEstado, BorderLayout.SOUTH);

            SwingUtilities.invokeLater(() -> {
                JFrame frame = new JFrame("Construcción Visual de la Red Social");
                frame.setLayout(new BorderLayout());
                frame.add(contenedor, BorderLayout.CENTER);
                frame.setPreferredSize(new Dimension(980, 800));
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                frame.setVisible(true);
            });

            Thread.sleep(600); // espera a que la ventana sea visible

            GeneradorPoblacion gen = new GeneradorPoblacion();
            gen.generarConFases(tamanoRed, semilla, (numFase, nombre, red) -> {
                setEstado(numFase, nombre, red.getTodosLosContactos().size());
                revelarFase(red, numFase, esGrande, pausaMs);
            });

            setEstado(0, "Construcción completa — cierra la ventana para continuar", -1);

        } catch (Exception e) {
            System.out.println("  [Visualización de construcción no disponible: " + e.getMessage() + "]");
        }
    }

    // ── Revelado incremental por fase ─────────────────────────────────────────

    /** Revela nodos (fase 1) o aristas (fases 2-6) de a uno para verlo construirse. */
    private void revelarFase(RedSocial red, int fase, boolean esGrande, int pausaMs) {
        if (fase == 1) {
            revelarNodos(red, esGrande);
        } else {
            revelarAristas(red, colorDeFase(fase), esGrande);
        }
        dormir(Math.min(pausaMs, 800)); // breve pausa al cerrar la fase
    }

    private void revelarNodos(RedSocial red, boolean esGrande) {
        int total = red.getTodasLasPersonas().size();
        int delay = (int) clamp(900.0 / Math.max(1, total), 8, 60);
        for (Persona p : red.getTodasLasPersonas()) {
            if (graph.getNode(p.getId()) != null) continue;
            Node n = graph.addNode(p.getId());
            if (!esGrande) n.setAttribute("ui.label", p.getId());
            n.setAttribute("ui.size", 10);
            if (esGrande) {
                double radio = Math.max(20, Math.sqrt(totalNodosEsperados) * 8);
                double angle = 2 * Math.PI * nodeCounter / Math.max(1, totalNodosEsperados);
                n.setAttribute("xyz", radio * Math.cos(angle), radio * Math.sin(angle), 0);
            }
            nodeCounter++;
            dormir(delay);
        }
    }

    private void revelarAristas(RedSocial red, String colorArista, boolean esGrande) {
        // Recolectar las aristas que son nuevas en esta fase
        java.util.List<Contacto> nuevas = new java.util.ArrayList<>();
        for (Contacto c : red.getTodosLosContactos()) {
            String eid = c.getOrigen().getId() + "_" + c.getDestino().getId();
            if (!aristasRenderizadas.contains(eid) && graph.getEdge(eid) == null) {
                nuevas.add(c);
            }
        }

        int delay = (int) clamp(1100.0 / Math.max(1, nuevas.size()), 6, 70);
        int tamMax = esGrande ? 22 : 46;

        for (Contacto c : nuevas) {
            String eid = c.getOrigen().getId() + "_" + c.getDestino().getId();
            try {
                int grosor = Math.max(1, (int) (c.getProbContagio() * 5));
                org.graphstream.graph.Edge e = graph.addEdge(
                        eid, c.getOrigen().getId(), c.getDestino().getId(), true);
                e.setAttribute("ui.style",
                        "size: " + grosor + "px; fill-color: " + colorArista + ";");
                aristasRenderizadas.add(eid);

                // Hacer crecer los dos extremos según su grado acumulado
                actualizarTamano(red, c.getOrigen(), tamMax);
                actualizarTamano(red, c.getDestino(), tamMax);
            } catch (Exception ignored) {}
            dormir(delay);
        }
    }

    private void actualizarTamano(RedSocial red, Persona p, int tamMax) {
        Node n = graph.getNode(p.getId());
        if (n != null) n.setAttribute("ui.size", Math.min(tamMax, 8 + red.getGrado(p)));
    }

    private static void dormir(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ── Barra de estado ───────────────────────────────────────────────────────

    private void setEstado(int fase, String nombre, int totalAristas) {
        if (etiquetaEstado == null) return;
        String txt = fase > 0
                ? String.format("Fase %d/6: %s  |  Aristas acumuladas: %d", fase, nombre, totalAristas)
                : nombre;
        SwingUtilities.invokeLater(() -> etiquetaEstado.setText(txt));
    }

    // ── Componentes Swing ─────────────────────────────────────────────────────

    private JLabel crearBarraEstado(String texto) {
        JLabel lbl = new JLabel(texto, SwingConstants.CENTER);
        lbl.setOpaque(true);
        lbl.setBackground(new Color(33, 37, 43));
        lbl.setForeground(new Color(220, 225, 235));
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 13f));
        lbl.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        return lbl;
    }

    private JLabel crearLeyenda() {
        JLabel lbl = new JLabel(
            "<html><center>"
            + "<b style='color:white'>Fases: &nbsp;</b>"
            + "<font color='#3498db'>&#9632; Nodos</font> &nbsp;&nbsp;"
            + "<font color='#e74c3c'>&#9632; Familia</font> &nbsp;&nbsp;"
            + "<font color='#e67e22'>&#9632; Vecindario</font> &nbsp;&nbsp;"
            + "<font color='#9b59b6'>&#9632; Hubs</font> &nbsp;&nbsp;"
            + "<font color='#95a5a6'>&#9632; Long-range</font> &nbsp;&nbsp;"
            + "<font color='#2ecc71'>&#9632; Puente</font>"
            + "</center></html>",
            SwingConstants.CENTER);
        lbl.setOpaque(true);
        lbl.setBackground(new Color(43, 47, 56));
        lbl.setFont(lbl.getFont().deriveFont(13f));
        lbl.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
        return lbl;
    }

    // ── Auxiliares ────────────────────────────────────────────────────────────

    private String colorDeFase(int fase) {
        if (fase >= 1 && fase < COLORES_FASE.length) return COLORES_FASE[fase];
        return "rgba(120,120,120,160)";
    }
}
