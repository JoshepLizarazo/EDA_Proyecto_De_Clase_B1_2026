package presentation;

import application.dto.ResultadoLoteDto;
import application.dto.ResultadoSimulacionDto;
import domain.value.EstrategiaVacunacion;
import infrastructure.persistence.ExportadorResultados;
import infrastructure.persistence.GeneradorReporteLotePDF;
import infrastructure.util.AnalisisComparativo;
import infrastructure.util.AnalisisComparativo.ScoreEstrategia;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
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
 * Ventana de resultados del experimento por lotes.
 *
 * Muestra, por estrategia, las métricas PROMEDIADAS sobre los N grafos distintos,
 * el conteo de victorias (ACUMULADO: en cuántas corridas cada estrategia obtuvo el
 * mejor score compuesto) y el ranking por score compuesto promedio. Incluye la
 * curva I(t) promedio y exportación a TXT/PDF de los promedios.
 */
public class VentanaResultadosLote extends JFrame {

    private final ResultadoLoteDto lote;
    private final List<ResultadoSimulacionDto> promedios;
    private final Map<EstrategiaVacunacion, Integer> victorias;

    private final ExportadorResultados   exportador   = new ExportadorResultados();
    private final GeneradorReporteLotePDF generadorPdf = new GeneradorReporteLotePDF();
    private final AnalisisComparativo    analisis     = new AnalisisComparativo();

    public VentanaResultadosLote(ResultadoLoteDto lote) {
        super("Resultados — Experimento por lotes");
        this.lote      = lote;
        this.promedios = lote.getPromedios();
        this.victorias = lote.getVictorias();
        construirUI();
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        pack();
        setMinimumSize(new Dimension(940, 640));
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
        String titulo = String.format(
                "Experimento por lotes — %d grafos × %d estrategias = %d simulaciones (grafos independientes)",
                lote.getNGrafosPorEstrategia(), promedios.size(), lote.getTotalSimulaciones());
        JLabel lbl = new JLabel(titulo, SwingConstants.CENTER);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 15f));
        lbl.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
        return lbl;
    }

    private JTabbedPane crearTabsCentral() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Tabla y ranking", crearPanelTablaRanking());
        tabs.addTab("Curva I(t) promedio", crearPanelGrafico());
        return tabs;
    }

    private JSplitPane crearPanelTablaRanking() {
        JTable tabla = new JTable(construirModeloTabla());
        tabla.setRowHeight(24);
        tabla.setFont(tabla.getFont().deriveFont(13f));
        tabla.getTableHeader().setFont(tabla.getFont().deriveFont(Font.BOLD));
        EstrategiaVacunacion ganador = estrategiaGanadora();
        if (ganador != null) {
            tabla.setDefaultRenderer(Object.class, new RendererGanador(ganador.name()));
        }
        JScrollPane scrollTabla = new JScrollPane(tabla);
        scrollTabla.setBorder(BorderFactory.createTitledBorder("Métricas promedio por estrategia"));

        JTextArea areaRanking = new JTextArea(construirTextoRanking());
        areaRanking.setEditable(false);
        areaRanking.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollRanking = new JScrollPane(areaRanking);
        scrollRanking.setBorder(BorderFactory.createTitledBorder("Ranking y ganador"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollTabla, scrollRanking);
        split.setResizeWeight(0.42);
        return split;
    }

    private DefaultTableModel construirModeloTabla() {
        String[] columnas = { "Estrategia", "Pico (prom)", "Turno-Pico", "Duración (prom)",
                              "Recuperados (prom)", "Contención %", "R0", "Victorias" };
        DefaultTableModel modelo = new DefaultTableModel(columnas, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        int n = lote.getNGrafosPorEstrategia();
        // Orden de la tabla = ranking por score compuesto (ganador arriba)
        for (ScoreEstrategia s : analisis.calcularRanking(promedios)) {
            ResultadoSimulacionDto r = porEstrategia(s.estrategia);
            if (r == null) continue;
            modelo.addRow(new Object[] {
                    r.getEstrategia().name(),
                    r.getPicoMaximoInfectados(),
                    r.getTurnoDePico(),
                    r.getDuracionBrote(),
                    r.getTotalRecuperados(),
                    String.format("%.1f", r.getPorcentajeContencion()),
                    String.format("%.2f", r.getR0Estimado()),
                    victorias.getOrDefault(r.getEstrategia(), 0) + " / " + n
            });
        }
        return modelo;
    }

    private String construirTextoRanking() {
        int n = lote.getNGrafosPorEstrategia();
        List<ScoreEstrategia> ranking = analisis.calcularRanking(promedios);

        StringBuilder sb = new StringBuilder();
        sb.append("ANÁLISIS DEL LOTE — promedio + acumulado\n");
        sb.append(String.format("%d grafos por estrategia · %d simulaciones · grafos independientes%n",
                n, lote.getTotalSimulaciones()));
        sb.append("───────────────────────────────────────────────────────────────────────\n");
        sb.append(String.format("%-3s %-14s | %-6s | %-9s | %s%n",
                "#", "Estrategia", "Score", "Victorias", "Componentes (pico/dur/afect/cont/R0)"));
        sb.append("───────────────────────────────────────────────────────────────────────\n");
        for (int i = 0; i < ranking.size(); i++) {
            ScoreEstrategia s = ranking.get(i);
            String marca = (i == 0) ? "★" : " ";
            sb.append(String.format("%s%-2d %-14s | %.3f | %4d/%-4d | %.2f / %.2f / %.2f / %.2f / %.2f%n",
                    marca, i + 1, s.estrategia, s.score,
                    victorias.getOrDefault(s.estrategia, 0), n,
                    s.componentes.getOrDefault("Pico", 0.0),
                    s.componentes.getOrDefault("Duración", 0.0),
                    s.componentes.getOrDefault("Afectados", 0.0),
                    s.componentes.getOrDefault("Contención", 0.0),
                    s.componentes.getOrDefault("R0", 0.0)));
        }
        sb.append('\n');
        sb.append(analisis.justificarGanador(ranking, promedios));
        sb.append("\n\n");
        sb.append("Nota: el score promedia las métricas de las N corridas de cada estrategia;\n");
        sb.append("las victorias cuentan en cuántas corridas la estrategia obtuvo el mejor score.\n");
        sb.append("Cada estrategia se evaluó sobre grafos distintos e independientes.");
        return sb.toString();
    }

    private ChartPanel crearPanelGrafico() {
        XYSeriesCollection dataset = new XYSeriesCollection();
        for (ResultadoSimulacionDto r : promedios) {
            XYSeries serie = new XYSeries(r.getEstrategia().name());
            List<Map<String, Integer>> hist = r.getHistorialPorTurno();
            for (int t = 0; t < hist.size(); t++) {
                serie.add(t, hist.get(t).getOrDefault("I", 0));
            }
            dataset.addSeries(serie);
        }

        JFreeChart chart = ChartFactory.createXYLineChart(
                "Curva de infectados I(t) — promedio del lote",
                "Turno", "Infectados (promedio)",
                dataset, PlotOrientation.VERTICAL, true, true, false);

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

    private JPanel crearBotonesInferiores() {
        JPanel panel = new JPanel(new GridLayout(1, 0, 12, 0));

        JButton btnTxt = new JButton("Exportar TXT");
        btnTxt.addActionListener(e -> exportarTxt());
        panel.add(btnTxt);

        JButton btnPdf = new JButton("Exportar PDF");
        btnPdf.addActionListener(e -> exportarPdf());
        panel.add(btnPdf);

        JButton btnCerrar = new JButton("Cerrar");
        btnCerrar.addActionListener(e -> dispose());
        panel.add(btnCerrar);
        return panel;
    }

    // ── Exportación (sobre los promedios) ──────────────────────────────────────

    private void exportarTxt() {
        File f = elegirDestino("resultados_lote.txt", "Archivos de texto", "txt");
        if (f == null) return;
        try {
            exportador.exportar(promedios, f.getAbsolutePath());
            JOptionPane.showMessageDialog(this, "Texto guardado en:\n" + f.getAbsolutePath(),
                    "Exportación exitosa", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error al exportar texto:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportarPdf() {
        File f = elegirDestino("reporte_lote.pdf", "Archivos PDF", "pdf");
        if (f == null) return;
        try {
            generadorPdf.exportar(lote, f.getAbsolutePath());
            JOptionPane.showMessageDialog(this, "PDF guardado en:\n" + f.getAbsolutePath(),
                    "Exportación exitosa", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error al exportar PDF:\n" + ex.getMessage(),
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

    // ── Auxiliares ─────────────────────────────────────────────────────────────

    private ResultadoSimulacionDto porEstrategia(EstrategiaVacunacion e) {
        for (ResultadoSimulacionDto r : promedios) {
            if (r.getEstrategia() == e) return r;
        }
        return null;
    }

    private EstrategiaVacunacion estrategiaGanadora() {
        List<ScoreEstrategia> ranking = analisis.calcularRanking(promedios);
        return ranking.isEmpty() ? null : ranking.get(0).estrategia;
    }

    private static Color colorUI(String clave, Color fallback) {
        Color c = javax.swing.UIManager.getColor(clave);
        return c != null ? c : fallback;
    }

    // ── Renderer para resaltar al ganador ─────────────────────────────────────

    private static class RendererGanador extends DefaultTableCellRenderer {
        private static final Color FONDO_GANADOR = new Color(90, 130, 70);
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
            boolean esGanador = estrategia != null && nombreGanador.equals(estrategia.toString());
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
