package presentation;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Ventana única con un {@link JTabbedPane} que aloja seis vistas de
 * {@link GraficoSimulacion} (una por estrategia de vacunación).
 *
 * Las pestañas se llenan secuencialmente: cada estrategia inicia su
 * simulación con la pestaña activa visible y, cuando termina, se mantiene
 * para la comparación final visual.
 */
public class VentanaComparativaTabs {

    private final JFrame frame;
    private final JTabbedPane tabs;
    private boolean disponible = false;

    public VentanaComparativaTabs() {
        JFrame f = null;
        JTabbedPane t = null;
        try {
            f = new JFrame("Comparativo SIRV — Estrategias de Vacunación");
            t = new JTabbedPane(JTabbedPane.TOP);
            f.getContentPane().setLayout(new BorderLayout());
            f.getContentPane().add(t, BorderLayout.CENTER);
            f.setPreferredSize(new Dimension(1100, 760));
            f.pack();
            f.setLocationRelativeTo(null);
            f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            f.setVisible(true);
            disponible = true;
        } catch (Exception e) {
            System.out.println("  [Ventana comparativa no disponible: " + e.getMessage() + "]");
            disponible = false;
        }
        this.frame = f;
        this.tabs  = t;
    }

    /**
     * Agrega una nueva pestaña al final y la deja activa para que el usuario
     * vea la simulación que está corriendo en ese momento.
     */
    public void agregarTab(String titulo, JComponent vista) {
        if (!disponible || vista == null) return;
        SwingUtilities.invokeLater(() -> {
            tabs.addTab(titulo, vista);
            int idx = tabs.indexOfComponent(vista);
            if (idx >= 0) tabs.setSelectedIndex(idx);
        });
    }

    public boolean isDisponible() { return disponible; }
}
