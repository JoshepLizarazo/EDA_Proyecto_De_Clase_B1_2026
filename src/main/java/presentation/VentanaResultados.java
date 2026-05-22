package presentation;

import application.dto.ResultadoSimulacionDto;
import infrastructure.persistence.ExportadorResultados;
import infrastructure.persistence.GeneradorReportePDF;
import infrastructure.util.AnalisisComparativo;
import infrastructure.util.AnalisisComparativo.ScoreEstrategia;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * Ventana de resultados Swing. Reemplaza la salida por consola de
 * {@code PanelEstadisticas} y la antigua oferta de exportación de
 * {@code ConsolaMenu}.
 *
 * Contiene:
 * <ul>
 *   <li>Tabla comparativa con las métricas de cada estrategia.</li>
 *   <li>Texto con el ranking y la justificación del ganador.</li>
 *   <li>Gráfico {@link JFreeChart} con la curva I(t) de cada estrategia.</li>
 *   <li>Botones para exportar a TXT y PDF.</li>
 * </ul>
 *
 * El botón de exportación a PDF se oculta cuando la simulación es individual
 * (una sola estrategia no produce un comparativo significativo).
 */
public class VentanaResultados extends JFrame {

    private final List<ResultadoSimulacionDto> resultados;
    private final boolean modoIndividual;

    private final ExportadorResultados exportador  = new ExportadorResultados();
    private final GeneradorReportePDF  generadorPdf = new GeneradorReportePDF();
    private final AnalisisComparativo  analisis    = new AnalisisComparativo();

    public VentanaResultados(List<ResultadoSimulacionDto> resultados, boolean modoIndividual) {
        super(modoIndividual ? "Resultado de la simulación" : "Resultados — Comparativo SIRV");
        this.resultados     = resultados;
        this.modoIndividual = modoIndividual;
        construirUI();
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        pack();
        setMinimumSize(new Dimension(900, 620));
        setLocationRelativeTo(null);
    }

    // ── Construcción de la UI ─────────────────────────────────────────────────

    private void construirUI() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        root.add(crearEncabezado(), BorderLayout.NORTH);
        root.add(crearTabsCentral(), BorderLayout.CENTER);
        root.add(crearBotonesInferiores(), BorderLayout.SOUTH);
        setContentPane(root);
    }

    private JLabel crearEncabezado() {
        String titulo = modoIndividual
                ? "Resultado de la estrategia: " + resultados.get(0).getEstrategia()
                : "Comparativo final — " + resultados.size() + " estrategias";
        JLabel lbl = new JLabel(titulo, SwingConstants.CENTER);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 16f));
        lbl.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
        return lbl;
    }

    private JTabbedPane crearTabsCentral() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Tabla y ranking", crearPanelTablaRanking());
        tabs.addTab("Curva I(t)",      crearPanelGrafico());
        return tabs;
    }

    private JSplitPane crearPanelTablaRanking() {
        JTable tabla = new JTable(construirModeloTabla());
        tabla.setRowHeight(24);
        tabla.setFont(tabla.getFont().deriveFont(13f));
        tabla.getTableHeader().setFont(tabla.getFont().deriveFont(Font.BOLD));
        // Resaltar la fila del ganador
        ResultadoSimulacionDto ganador = encontrarGanador();
        if (ganador != null) {
            tabla.setDefaultRenderer(Object.class, new RendererGanador(ganador.getEstrategia().name()));
        }
        JScrollPane scrollTabla = new JScrollPane(tabla);
        scrollTabla.setBorder(BorderFactory.createTitledBorder("Métricas por estrategia"));

        JTextArea areaRanking = new JTextArea(construirTextoRanking());
        areaRanking.setEditable(false);
        areaRanking.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollRanking = new JScrollPane(areaRanking);
        scrollRanking.setBorder(BorderFactory.createTitledBorder(
                modoIndividual ? "Detalle" : "Ranking y ganador"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollTabla, scrollRanking);
        split.setResizeWeight(0.45);
        return split;
    }

    private DefaultTableModel construirModeloTabla() {
        String[] columnas = { "Estrategia", "Pico", "Turno-Pico", "Duración",
                              "Recuperados", "Contención %", "R0", "Vacunados" };
        DefaultTableModel modelo = new DefaultTableModel(columnas, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (ResultadoSimulacionDto r : resultados) {
            modelo.addRow(new Object[] {
                    r.getEstrategia().name(),
                    r.getPicoMaximoInfectados(),
                    r.getTurnoDePico(),
                    r.getDuracionBrote(),
                    r.getTotalRecuperados(),
                    String.format("%.1f", r.getPorcentajeContencion()),
                    String.format("%.2f", r.getR0Estimado()),
                    r.getTotalVacunados()
            });
        }
        return modelo;
    }

    private ResultadoSimulacionDto encontrarGanador() {
        if (resultados.isEmpty()) return null;
        return resultados.stream()
                .min(Comparator.comparingInt(ResultadoSimulacionDto::getPicoMaximoInfectados))
                .orElse(null);
    }

    private String construirTextoRanking() {
        StringBuilder sb = new StringBuilder();
        if (modoIndividual) {
            ResultadoSimulacionDto r = resultados.get(0);
            sb.append("Estrategia        : ").append(r.getEstrategia()).append('\n');
            sb.append("Población total   : ").append(r.getTotalPoblacion()).append('\n');
            sb.append("Vacunados (20%)   : ").append(r.getTotalVacunados()).append('\n');
            sb.append("Pico infectados   : ").append(r.getPicoMaximoInfectados())
              .append(" (turno ").append(r.getTurnoDePico()).append(")\n");
            sb.append("Duración brote    : ").append(r.getDuracionBrote()).append(" turnos\n");
            sb.append("Recuperados total : ").append(r.getTotalRecuperados()).append('\n');
            sb.append(String.format("Contención        : %.1f%%%n", r.getPorcentajeContencion()));
            sb.append(String.format("R0 estimado       : %.2f%n",  r.getR0Estimado()));
            return sb.toString();
        }

        List<ScoreEstrategia> ranking = analisis.calcularRanking(resultados);
        sb.append("ANÁLISIS CUANTITATIVO — SCORE COMPUESTO\n");
        sb.append("───────────────────────────────────────────────────────────────────────\n");
        sb.append(String.format("%-14s | %-6s | %s%n",
                "Estrategia", "Score", "Componentes (pico/dur/afect/cont/R0)"));
        sb.append("───────────────────────────────────────────────────────────────────────\n");
        for (int i = 0; i < ranking.size(); i++) {
            ScoreEstrategia s = ranking.get(i);
            String marca = (i == 0) ? "★" : " ";
            sb.append(String.format("%s%-14s | %.3f | %.2f / %.2f / %.2f / %.2f / %.2f%n",
                    marca, s.estrategia, s.score,
                    s.componentes.getOrDefault("Pico", 0.0),
                    s.componentes.getOrDefault("Duración", 0.0),
                    s.componentes.getOrDefault("Afectados", 0.0),
                    s.componentes.getOrDefault("Contención", 0.0),
                    s.componentes.getOrDefault("R0", 0.0)));
        }
        sb.append('\n');
        sb.append(analisis.justificarGanador(ranking, resultados));
        return sb.toString();
    }

    private ChartPanel crearPanelGrafico() {
        XYSeriesCollection dataset = new XYSeriesCollection();
        for (ResultadoSimulacionDto r : resultados) {
            XYSeries serie = new XYSeries(r.getEstrategia().name());
            List<Map<String, Integer>> hist = r.getHistorialPorTurno();
            for (int t = 0; t < hist.size(); t++) {
                serie.add(t, hist.get(t).getOrDefault("I", 0));
            }
            dataset.addSeries(serie);
        }

        JFreeChart chart = ChartFactory.createXYLineChart(
                "Curva de infectados I(t)",
                "Turno",
                "Infectados",
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false);

        // Paleta oscura para que el gráfico encaje con FlatDarkLaf
        Color fondoVentana = colorUI("Panel.background", new Color(43, 43, 43));
        Color fondoPlot    = colorUI("Table.background", new Color(60, 63, 65));
        Color textoClaro   = colorUI("Label.foreground",  new Color(220, 220, 220));
        Color grid         = new Color(255, 255, 255, 40);

        chart.setBackgroundPaint(fondoVentana);
        chart.getTitle().setPaint(textoClaro);
        if (chart.getLegend() != null) {
            chart.getLegend().setBackgroundPaint(fondoVentana);
            chart.getLegend().setItemPaint(textoClaro);
        }

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(fondoPlot);
        plot.setDomainGridlinePaint(grid);
        plot.setRangeGridlinePaint(grid);
        plot.setOutlinePaint(grid);

        plot.getDomainAxis().setLabelPaint(textoClaro);
        plot.getDomainAxis().setTickLabelPaint(textoClaro);
        plot.getRangeAxis().setLabelPaint(textoClaro);
        plot.getRangeAxis().setTickLabelPaint(textoClaro);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        for (int i = 0; i < dataset.getSeriesCount(); i++) {
            renderer.setSeriesStroke(i, new java.awt.BasicStroke(2.0f));
        }
        plot.setRenderer(renderer);

        ChartPanel panel = new ChartPanel(chart);
        panel.setBackground(fondoVentana);
        return panel;
    }

    private static Color colorUI(String clave, Color fallback) {
        Color c = javax.swing.UIManager.getColor(clave);
        return c != null ? c : fallback;
    }

    private JPanel crearBotonesInferiores() {
        JPanel panel = new JPanel(new GridLayout(1, 0, 12, 0));

        JButton btnTxt = new JButton("Exportar TXT");
        btnTxt.addActionListener(e -> exportarTxt());
        panel.add(btnTxt);

        if (!modoIndividual) {
            JButton btnPdf = new JButton("Exportar PDF");
            btnPdf.addActionListener(e -> exportarPdf());
            panel.add(btnPdf);
        }

        JButton btnCerrar = new JButton("Cerrar");
        btnCerrar.addActionListener(e -> dispose());
        panel.add(btnCerrar);

        return panel;
    }

    // ── Exportación ───────────────────────────────────────────────────────────

    private void exportarTxt() {
        File f = elegirDestino("resultados_simulacion.txt", "Archivos de texto", "txt");
        if (f == null) return;
        try {
            exportador.exportar(resultados, f.getAbsolutePath());
            JOptionPane.showMessageDialog(this,
                    "Texto guardado en:\n" + f.getAbsolutePath(),
                    "Exportación exitosa", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al exportar texto:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportarPdf() {
        File f = elegirDestino("reporte_simulacion.pdf", "Archivos PDF", "pdf");
        if (f == null) return;
        try {
            generadorPdf.exportar(resultados, f.getAbsolutePath());
            JOptionPane.showMessageDialog(this,
                    "PDF guardado en:\n" + f.getAbsolutePath(),
                    "Exportación exitosa", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error al exportar PDF:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private File elegirDestino(String nombreSugerido, String descripcion, String ext) {
        JFileChooser chooser = new JFileChooser(".");
        chooser.setSelectedFile(new File(nombreSugerido));
        chooser.setFileFilter(new FileNameExtensionFilter(descripcion, ext));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return null;
        File f = chooser.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith("." + ext)) {
            f = new File(f.getParentFile(), f.getName() + "." + ext);
        }
        return f;
    }

    // ── Renderer para resaltar al ganador ─────────────────────────────────────

    private static class RendererGanador extends DefaultTableCellRenderer {
        private static final Color FONDO_GANADOR = new Color(90, 130, 70);  // verde oliva oscuro
        private static final Color TEXTO_GANADOR = new Color(245, 245, 230);
        private final String nombreGanador;

        RendererGanador(String nombreGanador) { this.nombreGanador = nombreGanador; }

        @Override
        public java.awt.Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus,
                int row, int column) {
            java.awt.Component c = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            Object estrategia = table.getModel().getValueAt(row, 0);
            boolean esGanador = estrategia != null
                    && nombreGanador.equals(estrategia.toString());
            if (!isSelected) {
                if (esGanador) {
                    c.setBackground(FONDO_GANADOR);
                    c.setForeground(TEXTO_GANADOR);
                } else {
                    c.setBackground(table.getBackground());
                    c.setForeground(table.getForeground());
                }
            }
            return c;
        }
    }
}
