package presentation;

import application.dto.ConfiguracionDto;
import domain.value.EstrategiaVacunacion;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;

/**
 * Diálogo modal de configuración de la simulación. Reemplaza la antigua
 * interacción por consola del método {@code solicitarConfiguracion} en
 * {@code ConsolaMenu}.
 *
 * Uso:
 * <pre>
 *   VentanaConfiguracion dlg = new VentanaConfiguracion(parent, individual);
 *   dlg.setVisible(true);
 *   ConfiguracionDto cfg = dlg.getConfiguracion(); // null si canceló
 * </pre>
 */
public class VentanaConfiguracion extends JDialog {

    private static final String[] TAMANOS = { "Red pequeña (~80 nodos)",
                                              "Red grande (~300 nodos)",
                                              "Personalizado" };

    private final ModoSimulacion modo;

    private final JComboBox<String> cbTamano       = new JComboBox<>(TAMANOS);
    private final JTextField        txtPersonas    = new JTextField("80");
    private final JComboBox<EstrategiaVacunacion> cbEstrategia =
            new JComboBox<>(EstrategiaVacunacion.values());
    private final JSpinner          spGrafos       = new JSpinner(new SpinnerNumberModel(10, 2, Integer.MAX_VALUE, 1));
    private final JSpinner          spTurnos       = new JSpinner(new SpinnerNumberModel(60, 1, 9999, 1));
    private final JSpinner          spRecuperacion = new JSpinner(new SpinnerNumberModel(7,  1, 365,  1));
    private final JSpinner          spPausaFases   = new JSpinner(new SpinnerNumberModel(600, 200, 10000, 100));
    private final JCheckBox         cbVisualizacion = new JCheckBox("Mostrar visualización GraphStream");
    private final JCheckBox         cbCSV           = new JCheckBox("Cargar red desde archivos CSV");

    private ConfiguracionDto resultado = null;

    public VentanaConfiguracion(java.awt.Frame padre, ModoSimulacion modo) {
        super(padre, "Configuración de la simulación", true);
        this.modo = modo;
        construirUI();
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        pack();
        setMinimumSize(new Dimension(520, getHeight()));
        setLocationRelativeTo(padre);
    }

    // ── Construcción de la UI ─────────────────────────────────────────────────

    private void construirUI() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        root.add(crearTitulo(),    BorderLayout.NORTH);
        root.add(crearFormulario(),BorderLayout.CENTER);
        root.add(crearBotones(),   BorderLayout.SOUTH);
        setContentPane(root);
    }

    private JLabel crearTitulo() {
        String texto = switch (modo) {
            case INDIVIDUAL          -> "Configura tu simulación individual";
            case COMPARATIVO         -> "Configura el comparativo de 6 estrategias";
            case LOTE                -> "Configura el experimento por lotes";
            case CONSTRUCCION_VISUAL -> "Configura la construcción visual de la red";
        };
        JLabel lbl = new JLabel(texto, JLabel.CENTER);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 14f));
        return lbl;
    }

    private JPanel crearFormulario() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets    = new Insets(6, 6, 6, 6);
        gbc.fill      = GridBagConstraints.HORIZONTAL;
        gbc.weightx   = 1.0;
        gbc.gridx     = 0;
        gbc.anchor    = GridBagConstraints.WEST;

        int row = 0;

        // Tamaño de red
        agregarFila(form, gbc, row++, "Tamaño de red:", cbTamano);
        cbTamano.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                actualizarVisibilidadPersonas();
            }
        });
        agregarFila(form, gbc, row++, "N personas (si personalizado):", txtPersonas);
        // Para el paso a paso conviene poca población: arranca en 25 personalizado.
        if (modo == ModoSimulacion.CONSTRUCCION_VISUAL) {
            cbTamano.setSelectedIndex(2);
            txtPersonas.setText("25");
            txtPersonas.setEnabled(true);
        } else {
            cbTamano.setSelectedIndex(0);
            txtPersonas.setEnabled(false);
        }

        // Estrategia — solo individual; no aplica en construcción visual
        if (modo == ModoSimulacion.INDIVIDUAL) {
            agregarFila(form, gbc, row++, "Estrategia de vacunación:", cbEstrategia);
        } else if (modo != ModoSimulacion.CONSTRUCCION_VISUAL) {
            String txt = (modo == ModoSimulacion.LOTE)
                    ? "(Cada estrategia corre N grafos distintos)"
                    : "(Se ejecutarán las 6 estrategias automáticamente)";
            JLabel info = new JLabel(txt);
            java.awt.Color secundario =
                    javax.swing.UIManager.getColor("Label.disabledForeground");
            if (secundario == null) secundario = new java.awt.Color(160, 160, 170);
            info.setForeground(secundario);
            agregarFila(form, gbc, row++, "Estrategia:", info);
        }

        // Número de grafos por estrategia (solo lote)
        if (modo == ModoSimulacion.LOTE) {
            agregarFila(form, gbc, row++, "Grafos por estrategia:", spGrafos);
        }

        // Pausa entre fases (solo construcción visual)
        if (modo == ModoSimulacion.CONSTRUCCION_VISUAL) {
            agregarFila(form, gbc, row++, "Pausa entre fases (ms):", spPausaFases);
        }

        // Turnos y recuperación no aplican en construcción visual
        if (modo != ModoSimulacion.CONSTRUCCION_VISUAL) {
            agregarFila(form, gbc, row++, "Turnos máximos:", spTurnos);
            agregarFila(form, gbc, row++, "Días de recuperación:", spRecuperacion);
        }

        // Visualización y CSV no aplican en lote ni en construcción visual
        if (modo != ModoSimulacion.LOTE && modo != ModoSimulacion.CONSTRUCCION_VISUAL) {
            cbVisualizacion.setSelected(true);
            gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
            form.add(cbVisualizacion, gbc);

            gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
            form.add(cbCSV, gbc);
            gbc.gridwidth = 1;
        }

        return form;
    }

    private void agregarFila(JPanel panel, GridBagConstraints gbc, int row,
                             String etiqueta, java.awt.Component componente) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        panel.add(new JLabel(etiqueta), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        panel.add(componente, gbc);
    }

    private void actualizarVisibilidadPersonas() {
        boolean personalizado = cbTamano.getSelectedIndex() == 2;
        txtPersonas.setEnabled(personalizado);
        if (!personalizado) {
            txtPersonas.setText(cbTamano.getSelectedIndex() == 0 ? "80" : "300");
        }
    }

    private JPanel crearBotones() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 12, 0));
        JButton btnCancelar = new JButton("Cancelar");
        JButton btnEjecutar = new JButton("Ejecutar ▶");
        btnEjecutar.setFont(btnEjecutar.getFont().deriveFont(Font.BOLD));
        btnEjecutar.putClientProperty("JButton.buttonType", "default");
        getRootPane().setDefaultButton(btnEjecutar);

        btnCancelar.addActionListener(e -> { resultado = null; dispose(); });
        btnEjecutar.addActionListener(e -> {
            resultado = construirConfig();
            if (resultado != null) dispose();
        });
        panel.add(btnCancelar);
        panel.add(btnEjecutar);
        return panel;
    }

    // ── Construcción del DTO ──────────────────────────────────────────────────

    private ConfiguracionDto construirConfig() {
        ConfiguracionDto cfg = new ConfiguracionDto();

        int tamano;
        switch (cbTamano.getSelectedIndex()) {
            case 1 -> tamano = 300;
            case 2 -> {
                try {
                    tamano = Math.max(10, Integer.parseInt(txtPersonas.getText().trim()));
                } catch (NumberFormatException ex) {
                    javax.swing.JOptionPane.showMessageDialog(this,
                            "Cantidad de personas inválida. Debe ser un entero >= 10.",
                            "Error", javax.swing.JOptionPane.ERROR_MESSAGE);
                    return null;
                }
            }
            default -> tamano = 80;
        }
        cfg.setTamanoRed(tamano);   // recalcula el paciente cero al 5% de la población

        if (modo == ModoSimulacion.INDIVIDUAL) {
            cfg.setEstrategia((EstrategiaVacunacion) cbEstrategia.getSelectedItem());
        }
        cfg.setTurnosMaximos((Integer) spTurnos.getValue());
        cfg.setDiasRecuperacion((Integer) spRecuperacion.getValue());
        cfg.setMostrarVisualizacion(modo != ModoSimulacion.LOTE && cbVisualizacion.isSelected());
        cfg.setUsarCSV(modo != ModoSimulacion.LOTE && cbCSV.isSelected());
        return cfg;
    }

    /** @return la configuración construida, o {@code null} si el usuario canceló. */
    public ConfiguracionDto getConfiguracion() {
        return resultado;
    }

    /** Número de grafos por estrategia (solo relevante en modo lote). */
    public int getNGrafos() {
        return (Integer) spGrafos.getValue();
    }

    /** Pausa en ms entre fases (solo relevante en modo construcción visual). */
    public int getPausaFasesMs() {
        return (Integer) spPausaFases.getValue();
    }
}
