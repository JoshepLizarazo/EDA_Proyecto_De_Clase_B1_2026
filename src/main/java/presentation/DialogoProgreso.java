package presentation;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * Diálogo modal con una barra de progreso. Arranca indeterminado (para la
 * simulación individual y el comparativo) y puede pasar a modo determinado con
 * {@link #actualizar(int, int, String)} para reflejar el avance del experimento
 * por lotes (corrida X de N).
 */
public class DialogoProgreso extends JDialog {

    private final JLabel lbl;
    private final JProgressBar barra;

    public DialogoProgreso(Frame padre, String mensaje) {
        super(padre, "Procesando", true);
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 28, 20, 28));

        lbl = new JLabel(mensaje, SwingConstants.CENTER);
        barra = new JProgressBar();
        barra.setIndeterminate(true);

        panel.add(lbl, BorderLayout.NORTH);
        panel.add(barra, BorderLayout.CENTER);

        setContentPane(panel);
        setPreferredSize(new Dimension(380, 120));
        pack();
        setLocationRelativeTo(padre);
    }

    /**
     * Refleja el avance del lote. La primera llamada convierte la barra a modo
     * determinado. Seguro de invocar desde un hilo de fondo.
     */
    public void actualizar(int completadas, int total, String detalle) {
        SwingUtilities.invokeLater(() -> {
            if (barra.isIndeterminate()) {
                barra.setIndeterminate(false);
                barra.setStringPainted(true);
                barra.setMinimum(0);
                barra.setMaximum(Math.max(1, total));
            }
            barra.setValue(completadas);
            if (detalle != null) lbl.setText(detalle);
        });
    }
}
