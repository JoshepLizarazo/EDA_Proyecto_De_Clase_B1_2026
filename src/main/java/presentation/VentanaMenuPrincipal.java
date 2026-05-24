package presentation;

import application.command.IniciarSimulacionCommand;
import application.dto.ConfiguracionDto;
import application.dto.ResultadoLoteDto;
import application.dto.ResultadoSimulacionDto;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

/**
 * Ventana principal Swing. Reemplaza la antigua interacción por consola.
 *
 * Permite al usuario elegir entre simulación individual o comparativa, abre
 * el diálogo de configuración y lanza la simulación en un hilo de fondo para
 * no congelar la UI. Al finalizar, muestra los resultados en {@link VentanaResultados}.
 */
public class VentanaMenuPrincipal extends JFrame {

    private final JRadioButton rbIndividual    = new JRadioButton("Simulación individual", true);
    private final JRadioButton rbComparativo   = new JRadioButton("Comparativo 6 estrategias");
    private final JRadioButton rbLote          = new JRadioButton("Experimento por lotes (N grafos por estrategia)");

    public VentanaMenuPrincipal() {
        super("Simulador SIRV — Menú Principal");
        construirUI();
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { dispose(); }
        });
        pack();
        setMinimumSize(new Dimension(520, 420));
        setLocationRelativeTo(null);
    }

    // ── Construcción de la UI ─────────────────────────────────────────────────

    private void construirUI() {
        JPanel contenedor = new JPanel(new BorderLayout(0, 16));
        contenedor.setBorder(BorderFactory.createEmptyBorder(20, 28, 20, 28));
        contenedor.add(crearBanner(), BorderLayout.NORTH);
        contenedor.add(crearPanelOpciones(), BorderLayout.CENTER);
        contenedor.add(crearPanelBotones(), BorderLayout.SOUTH);
        setContentPane(contenedor);
    }

    private JPanel crearBanner() {
        JPanel banner = new JPanel(new GridLayout(0, 1));
        banner.setOpaque(true);
        banner.setBackground(new Color(50, 95, 170)); // azul accent FlatLaf
        banner.setBorder(BorderFactory.createEmptyBorder(16, 12, 16, 12));

        JLabel titulo = new JLabel("SIMULADOR DE PROPAGACIÓN EPIDÉMICA — SIRV", SwingConstants.CENTER);
        titulo.setForeground(Color.WHITE);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 16f));

        JLabel subtitulo1 = new JLabel("Universidad Industrial de Santander — UIS", SwingConstants.CENTER);
        subtitulo1.setForeground(new Color(220, 230, 245));

        JLabel subtitulo2 = new JLabel("Estructuras de Datos y Análisis de Algoritmos", SwingConstants.CENTER);
        subtitulo2.setForeground(new Color(220, 230, 245));

        banner.add(titulo);
        banner.add(subtitulo1);
        banner.add(subtitulo2);
        return banner;
    }

    private JPanel crearPanelOpciones() {
        ButtonGroup grupo = new ButtonGroup();
        grupo.add(rbIndividual);
        grupo.add(rbComparativo);
        grupo.add(rbLote);

        rbIndividual.setFont(rbIndividual.getFont().deriveFont(Font.PLAIN, 14f));
        rbComparativo.setFont(rbComparativo.getFont().deriveFont(Font.PLAIN, 14f));
        rbLote.setFont(rbLote.getFont().deriveFont(Font.PLAIN, 14f));
        rbIndividual.setAlignmentX(Component.LEFT_ALIGNMENT);
        rbComparativo.setAlignmentX(Component.LEFT_ALIGNMENT);
        rbLote.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel descIndiv = new JLabel(
                "   Ejecuta una sola estrategia de vacunación que tú elijas.");
        JLabel descComp  = new JLabel(
                "   Ejecuta las 6 estrategias sobre la misma red (mismos infectados).");
        JLabel descLote  = new JLabel(
                "   Corre N grafos distintos por estrategia y promedia los resultados.");
        Color secundario = javax.swing.UIManager.getColor("Label.disabledForeground");
        if (secundario == null) secundario = new Color(160, 160, 170);
        descIndiv.setForeground(secundario);
        descComp.setForeground(secundario);
        descLote.setForeground(secundario);
        descIndiv.setAlignmentX(Component.LEFT_ALIGNMENT);
        descComp.setAlignmentX(Component.LEFT_ALIGNMENT);
        descLote.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Contenido apilado vertical, compacto en su altura preferida
        JPanel contenido = new JPanel();
        contenido.setLayout(new BoxLayout(contenido, BoxLayout.Y_AXIS));
        contenido.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        contenido.add(rbIndividual);
        contenido.add(Box.createVerticalStrut(2));
        contenido.add(descIndiv);
        contenido.add(Box.createVerticalStrut(16));
        contenido.add(rbComparativo);
        contenido.add(Box.createVerticalStrut(2));
        contenido.add(descComp);
        contenido.add(Box.createVerticalStrut(16));
        contenido.add(rbLote);
        contenido.add(Box.createVerticalStrut(2));
        contenido.add(descLote);

        // Wrapper: deja el contenido pegado arriba; el sobrante queda vacío.
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createTitledBorder("Modo de simulación"));
        wrapper.add(contenido, BorderLayout.NORTH);
        return wrapper;
    }

    private JPanel crearPanelBotones() {
        // FlowLayout.RIGHT mantiene el tamaño preferido de cada botón
        // y los alinea a la derecha, sin estirarse cuando la ventana es ancha.
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));

        JButton btnContinuar = new JButton("Continuar →");
        btnContinuar.setFont(btnContinuar.getFont().deriveFont(Font.BOLD, 14f));
        btnContinuar.setPreferredSize(new Dimension(180, 38));
        btnContinuar.addActionListener(e -> onContinuar());
        btnContinuar.putClientProperty("JButton.buttonType", "default");
        getRootPane().setDefaultButton(btnContinuar);

        JButton btnSalir = new JButton("Salir");
        btnSalir.setPreferredSize(new Dimension(130, 38));
        btnSalir.addActionListener(e -> dispose());

        panel.add(btnSalir);
        panel.add(btnContinuar);
        return panel;
    }

    // ── Lógica de flujo ───────────────────────────────────────────────────────

    private void onContinuar() {
        ModoSimulacion modo = modoSeleccionado();
        VentanaConfiguracion dlg = new VentanaConfiguracion(this, modo);
        dlg.setVisible(true);

        ConfiguracionDto config = dlg.getConfiguracion();
        if (config == null) return; // Usuario canceló

        if (modo == ModoSimulacion.LOTE) {
            ejecutarLote(config, dlg.getNGrafos());
        } else {
            ejecutarSimulacion(config, modo);
        }
    }

    private ModoSimulacion modoSeleccionado() {
        if (rbComparativo.isSelected()) return ModoSimulacion.COMPARATIVO;
        if (rbLote.isSelected())        return ModoSimulacion.LOTE;
        return ModoSimulacion.INDIVIDUAL;
    }

    private void ejecutarSimulacion(ConfiguracionDto config, ModoSimulacion modo) {
        boolean individual = modo == ModoSimulacion.INDIVIDUAL;
        DialogoProgreso progreso = new DialogoProgreso(this,
                individual ? "Ejecutando simulación..." : "Ejecutando 6 estrategias...");

        SwingWorker<List<ResultadoSimulacionDto>, Void> worker =
                new SwingWorker<List<ResultadoSimulacionDto>, Void>() {

            @Override
            protected List<ResultadoSimulacionDto> doInBackground() {
                IniciarSimulacionCommand cmd =
                        new IniciarSimulacionCommand(config.getSemillaAleatoria());
                if (individual) {
                    GraficoSimulacion grafico =
                            config.isMostrarVisualizacion() ? new GraficoSimulacion() : null;
                    ResultadoSimulacionDto r = cmd.ejecutar(config, grafico);
                    return List.of(r);
                } else {
                    return cmd.ejecutarComparativo(config);
                }
            }

            @Override
            protected void done() {
                progreso.dispose();
                try {
                    List<ResultadoSimulacionDto> resultados = get();
                    new VentanaResultados(resultados, individual).setVisible(true);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(VentanaMenuPrincipal.this,
                            "Error al ejecutar la simulación:\n" + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
        progreso.setVisible(true);
    }

    private void ejecutarLote(ConfiguracionDto config, int nGrafos) {
        DialogoProgreso progreso = new DialogoProgreso(this, "Preparando experimento por lotes...");

        SwingWorker<ResultadoLoteDto, Void> worker = new SwingWorker<ResultadoLoteDto, Void>() {

            @Override
            protected ResultadoLoteDto doInBackground() {
                IniciarSimulacionCommand cmd =
                        new IniciarSimulacionCommand(config.getSemillaAleatoria());
                return cmd.ejecutarLote(config, nGrafos,
                        (hechas, total, detalle) -> progreso.actualizar(hechas, total,
                                String.format("Simulando %d / %d    %s", hechas, total, detalle)));
            }

            @Override
            protected void done() {
                progreso.dispose();
                try {
                    new VentanaResultadosLote(get()).setVisible(true);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(VentanaMenuPrincipal.this,
                            "Error al ejecutar el experimento por lotes:\n" + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
        progreso.setVisible(true);
    }

    // ── Punto de entrada estático ─────────────────────────────────────────────

    public static void mostrar() {
        javax.swing.SwingUtilities.invokeLater(() ->
                new VentanaMenuPrincipal().setVisible(true));
    }

    // ── Utilidad para centrar componentes ─────────────────────────────────────

    @SuppressWarnings("unused")
    private static void centrar(Component c) {
        if (c instanceof javax.swing.JComponent jc) {
            jc.setAlignmentX(Component.CENTER_ALIGNMENT);
        }
    }
}
