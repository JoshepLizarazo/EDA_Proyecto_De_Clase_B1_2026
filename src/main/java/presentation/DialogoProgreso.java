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

/**
 * Diálogo modal con una barra de progreso indeterminada. Se usa mientras una
 * simulación corre en background para impedir que el usuario interactúe con
 * la ventana principal y para dar feedback visual.
 */
public class DialogoProgreso extends JDialog {

    public DialogoProgreso(Frame padre, String mensaje) {
        super(padre, "Procesando", true);
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 28, 20, 28));

        JLabel lbl = new JLabel(mensaje, SwingConstants.CENTER);
        JProgressBar barra = new JProgressBar();
        barra.setIndeterminate(true);

        panel.add(lbl, BorderLayout.NORTH);
        panel.add(barra, BorderLayout.CENTER);

        setContentPane(panel);
        setPreferredSize(new Dimension(320, 110));
        pack();
        setLocationRelativeTo(padre);
    }
}
