package infrastructure.persistence;

import application.dto.ResultadoSimulacionDto;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import infrastructure.util.AnalisisComparativo;
import infrastructure.util.AnalisisComparativo.ScoreEstrategia;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Paint;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.labels.ItemLabelAnchor;
import org.jfree.chart.labels.ItemLabelPosition;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * Genera un reporte PDF profesional con gráficos de las simulaciones comparativas.
 *
 * Estructura del PDF:
 *   1. Portada y configuración
 *   2. Tabla resumen y declaración del ganador
 *   3. Curva comparativa I(t) (todas las estrategias superpuestas)
 *   4. Curvas SIRV por estrategia
 *   5. Comparativas de cada métrica (barras)
 *   6. Score compuesto con desglose por componente
 */
public class GeneradorReportePDF {

    // ── Paleta y constantes de estilo ─────────────────────────────────────────

    /** Color asignado a cada estrategia (misma posición que la lista de resultados). */
    private static final Color[] PALETA = {
        new Color(0x3498db), new Color(0xe74c3c), new Color(0x2ecc71),
        new Color(0xf39c12), new Color(0x9b59b6), new Color(0x1abc9c)
    };

    private static final Color COLOR_TITULO    = new Color(0x111827);
    private static final Color COLOR_BODY      = new Color(0x374151);
    private static final Color COLOR_SUAVE     = new Color(0x6b7280);
    private static final Color COLOR_GRID      = new Color(0xe5e7eb);
    private static final Color COLOR_FONDO     = new Color(0xfafbfc);
    private static final Color COLOR_AXIS      = new Color(0xd1d5db);
    private static final Color COLOR_ACENTO    = new Color(0x2c5fa6);
    private static final Color COLOR_ACENTO_T  = new Color(0xdce6f5);
    private static final Color COLOR_GANA_BG   = new Color(0xdcfce7);
    private static final Color COLOR_GANA_TXT  = new Color(0x166534);
    private static final Color COLOR_ZEBRA     = new Color(0xf6f7f9);
    private static final Color COLOR_TBL_HEAD  = new Color(0x2c5fa6);

    private static final java.awt.Font FUENTE_TITULO_GRAFICO =
            new java.awt.Font("SansSerif", java.awt.Font.BOLD, 13);
    private static final java.awt.Font FUENTE_EJE_TITULO =
            new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 10);
    private static final java.awt.Font FUENTE_EJE =
            new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 10);
    private static final java.awt.Font FUENTE_LABEL_BARRA =
            new java.awt.Font("SansSerif", java.awt.Font.BOLD, 10);
    private static final java.awt.Font FUENTE_LEYENDA =
            new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11);

    // ── API pública ───────────────────────────────────────────────────────────

    public void exportar(List<ResultadoSimulacionDto> resultados, String rutaPdf) throws IOException {
        if (resultados == null || resultados.isEmpty()) {
            throw new IOException("Sin resultados para exportar");
        }

        Document doc = new Document(PageSize.A4, 36, 36, 50, 50);
        try (FileOutputStream fos = new FileOutputStream(rutaPdf)) {
            PdfWriter.getInstance(doc, fos);
            doc.open();

            agregarPortada(doc, resultados);
            agregarTablaResumen(doc, resultados);
            agregarAnalisisGanador(doc, resultados);
            doc.newPage();

            agregarGraficoCurvaIComparativa(doc, resultados);
            doc.newPage();

            agregarCurvasSIRVPorEstrategia(doc, resultados);
            doc.newPage();

            agregarBarrasMetricas(doc, resultados);
            doc.newPage();

            agregarDesgloseScore(doc, resultados);
            doc.newPage();

            agregarGlosario(doc);

            doc.close();
        } catch (Exception e) {
            throw new IOException("Error generando PDF: " + e.getMessage(), e);
        }
    }

    // ── Portada ───────────────────────────────────────────────────────────────

    private void agregarPortada(Document doc, List<ResultadoSimulacionDto> resultados) throws Exception {
        // Banda hero con título en blanco sobre fondo azul accent
        Font fHeroTit = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Color.WHITE);
        Font fHeroSub = FontFactory.getFont(FontFactory.HELVETICA, 12, COLOR_ACENTO_T);

        PdfPTable banner = new PdfPTable(1);
        banner.setWidthPercentage(100);
        banner.setSpacingAfter(24);

        PdfPCell heroCell = new PdfPCell();
        heroCell.setBackgroundColor(COLOR_ACENTO);
        heroCell.setBorder(0);
        heroCell.setPadding(22);

        Paragraph pT = new Paragraph("Reporte de Simulación Epidémica SIRV", fHeroTit);
        pT.setAlignment(Element.ALIGN_CENTER);
        heroCell.addElement(pT);

        Paragraph pS = new Paragraph(
                "Comparativo de Estrategias de Vacunación — Universidad Industrial de Santander",
                fHeroSub);
        pS.setAlignment(Element.ALIGN_CENTER);
        heroCell.addElement(pS);

        banner.addCell(heroCell);
        doc.add(banner);

        // Caja de metadatos en tabla 2 columnas (etiqueta / valor)
        Font fLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, COLOR_SUAVE);
        Font fValor = FontFactory.getFont(FontFactory.HELVETICA, 12, COLOR_BODY);

        PdfPTable meta = new PdfPTable(new float[]{1f, 2f});
        meta.setWidthPercentage(85);
        meta.setHorizontalAlignment(Element.ALIGN_CENTER);
        meta.setSpacingAfter(18);

        String fecha = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        int totalPob = resultados.get(0).getTotalPoblacion();

        agregarMetaFila(meta, "Generado",              fecha,                          fLabel, fValor);
        agregarMetaFila(meta, "Tamaño de la red",      totalPob + " personas",         fLabel, fValor);
        agregarMetaFila(meta, "Estrategias evaluadas", String.valueOf(resultados.size()), fLabel, fValor);
        doc.add(meta);
    }

    private void agregarMetaFila(PdfPTable t, String etiqueta, String valor,
                                 Font fLabel, Font fValor) {
        PdfPCell c1 = new PdfPCell(new Phrase(etiqueta.toUpperCase(), fLabel));
        c1.setBorder(0);
        c1.setPaddingTop(4); c1.setPaddingBottom(4);
        c1.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(c1);

        PdfPCell c2 = new PdfPCell(new Phrase(valor, fValor));
        c2.setBorder(0);
        c2.setPaddingTop(4); c2.setPaddingBottom(4);
        c2.setPaddingLeft(10);
        t.addCell(c2);
    }

    // ── Tabla resumen ─────────────────────────────────────────────────────────

    private void agregarTablaResumen(Document doc, List<ResultadoSimulacionDto> resultados) throws Exception {
        Font fEnc   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
        Font fCelda = FontFactory.getFont(FontFactory.HELVETICA, 10, COLOR_BODY);
        Font fGana  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, COLOR_GANA_TXT);
        Font fH2    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, COLOR_TITULO);

        Paragraph h = new Paragraph("Tabla resumen de métricas", fH2);
        h.setSpacingBefore(4);
        h.setSpacingAfter(10);
        doc.add(h);

        PdfPTable tabla = new PdfPTable(new float[]{0.35f, 2.4f, 1f, 1f, 1.1f, 1.2f, 1.2f, 1f});
        tabla.setWidthPercentage(100);
        tabla.setSpacingAfter(6);

        String[] cabeceras = {"", "Estrategia", "Pico", "t-Pico", "Duración", "Afectados", "Contención%", "R0"};
        for (String c : cabeceras) {
            PdfPCell celda = new PdfPCell(new Phrase(c, fEnc));
            celda.setBackgroundColor(COLOR_TBL_HEAD);
            celda.setBorderColor(COLOR_TBL_HEAD);
            celda.setHorizontalAlignment(Element.ALIGN_CENTER);
            celda.setPadding(7);
            tabla.addCell(celda);
        }

        ResultadoSimulacionDto ganador = resultados.stream()
                .min(Comparator.comparingInt(ResultadoSimulacionDto::getPicoMaximoInfectados))
                .orElse(null);

        boolean alt = false;
        for (int i = 0; i < resultados.size(); i++) {
            ResultadoSimulacionDto r = resultados.get(i);
            boolean esGanador = r.equals(ganador);
            Color bg = esGanador ? COLOR_GANA_BG : (alt ? COLOR_ZEBRA : Color.WHITE);
            Font fil = esGanador ? fGana : fCelda;
            alt = !alt;

            // Columna 0: cuadrito de color por estrategia
            PdfPCell color = new PdfPCell(new Phrase(" "));
            color.setBackgroundColor(PALETA[i % PALETA.length]);
            color.setBorderColor(bg);
            color.setFixedHeight(20);
            tabla.addCell(color);

            String nombre = (esGanador ? "* " : "  ") + r.getEstrategia().name();
            agregarCelda(tabla, nombre,                                          fil, bg, Element.ALIGN_LEFT);
            agregarCelda(tabla, String.valueOf(r.getPicoMaximoInfectados()),     fil, bg, Element.ALIGN_CENTER);
            agregarCelda(tabla, String.valueOf(r.getTurnoDePico()),              fil, bg, Element.ALIGN_CENTER);
            agregarCelda(tabla, String.valueOf(r.getDuracionBrote()),            fil, bg, Element.ALIGN_CENTER);
            agregarCelda(tabla, String.valueOf(r.getTotalRecuperados()),         fil, bg, Element.ALIGN_CENTER);
            agregarCelda(tabla, String.format("%.1f%%", r.getPorcentajeContencion()), fil, bg, Element.ALIGN_CENTER);
            agregarCelda(tabla, String.format("%.2f",  r.getR0Estimado()),       fil, bg, Element.ALIGN_CENTER);
        }
        doc.add(tabla);

        Paragraph nota = new Paragraph(
                "* fila destacada = estrategia con menor pico de infectados.",
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, COLOR_SUAVE));
        nota.setSpacingAfter(10);
        doc.add(nota);
    }

    private void agregarCelda(PdfPTable tabla, String texto, Font font, Color bg, int align) {
        PdfPCell c = new PdfPCell(new Phrase(texto, font));
        c.setBackgroundColor(bg);
        c.setBorderColor(new Color(0xeceff3));
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(6);
        tabla.addCell(c);
    }

    private void agregarAnalisisGanador(Document doc, List<ResultadoSimulacionDto> resultados) throws Exception {
        Font fH2  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, COLOR_TITULO);
        Font fJus = FontFactory.getFont(FontFactory.HELVETICA, 11, COLOR_BODY);
        Font fGan = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, new Color(0x166534));

        AnalisisComparativo analisis = new AnalisisComparativo();
        List<ScoreEstrategia> ranking = analisis.calcularRanking(resultados);
        if (ranking.isEmpty()) return;

        ScoreEstrategia ganador = ranking.get(0);

        Paragraph h = new Paragraph("Estrategia óptima identificada", fH2);
        h.setSpacingBefore(14);
        h.setSpacingAfter(8);
        doc.add(h);

        Paragraph gan = new Paragraph(String.format(
            "* %s  —  score compuesto %.3f / 1.000", ganador.estrategia, ganador.score), fGan);
        gan.setSpacingAfter(6);
        doc.add(gan);

        Paragraph jus = new Paragraph(analisis.justificarGanador(ranking, resultados), fJus);
        jus.setSpacingAfter(6);
        doc.add(jus);
    }

    // ── Gráficos de líneas ────────────────────────────────────────────────────

    private void agregarGraficoCurvaIComparativa(Document doc, List<ResultadoSimulacionDto> resultados) throws Exception {
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
            "Curva de Infectados I(t) por estrategia",
            "Turno", "Infectados activos",
            dataset, PlotOrientation.VERTICAL, true, true, false);
        aplicarEstiloXY(chart, true);
        doc.add(jfreechartAImagen(chart, 520, 340));
    }

    private void agregarCurvasSIRVPorEstrategia(Document doc, List<ResultadoSimulacionDto> resultados) throws Exception {
        Font fH2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, COLOR_TITULO);
        Paragraph h = new Paragraph("Curvas SIRV por estrategia", fH2);
        h.setSpacingAfter(10);
        doc.add(h);

        for (ResultadoSimulacionDto r : resultados) {
            XYSeriesCollection ds = new XYSeriesCollection();
            XYSeries sS = new XYSeries("S — susceptibles");
            XYSeries sI = new XYSeries("I — infectados");
            XYSeries sR = new XYSeries("R — recuperados");
            XYSeries sV = new XYSeries("V — vacunados");
            List<Map<String, Integer>> hist = r.getHistorialPorTurno();
            for (int t = 0; t < hist.size(); t++) {
                sS.add(t, hist.get(t).getOrDefault("S", 0));
                sI.add(t, hist.get(t).getOrDefault("I", 0));
                sR.add(t, hist.get(t).getOrDefault("R", 0));
                sV.add(t, hist.get(t).getOrDefault("V", 0));
            }
            ds.addSeries(sS); ds.addSeries(sI); ds.addSeries(sR); ds.addSeries(sV);

            JFreeChart chart = ChartFactory.createXYLineChart(
                r.getEstrategia().name(),
                "Turno", "Personas",
                ds, PlotOrientation.VERTICAL, true, true, false);

            aplicarEstiloXY(chart, false);
            // Override: SIRV usa colores fijos por estado
            XYPlot plot = chart.getXYPlot();
            XYLineAndShapeRenderer rend = (XYLineAndShapeRenderer) plot.getRenderer();
            rend.setSeriesPaint(0, new Color(0x3498db));
            rend.setSeriesPaint(1, new Color(0xe74c3c));
            rend.setSeriesPaint(2, new Color(0x2ecc71));
            rend.setSeriesPaint(3, new Color(0xf39c12));

            doc.add(jfreechartAImagen(chart, 520, 210));
        }
    }

    // ── Barras de métricas ────────────────────────────────────────────────────

    private void agregarBarrasMetricas(Document doc, List<ResultadoSimulacionDto> resultados) throws Exception {
        Font fH2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, COLOR_TITULO);
        Paragraph h = new Paragraph("Comparativo por métrica", fH2);
        h.setSpacingAfter(8);
        doc.add(h);

        doc.add(crearLeyendaEstrategias(resultados));

        doc.add(barChart("Pico máximo de infectados", "Pico", resultados,
                r -> (double) r.getPicoMaximoInfectados(), 520, 230));
        doc.add(barChart("Duración del brote (turnos)", "Duración", resultados,
                r -> (double) r.getDuracionBrote(), 520, 230));
        doc.add(barChart("Total de afectados", "Afectados", resultados,
                r -> (double) r.getTotalRecuperados(), 520, 230));
        doc.add(barChart("Porcentaje de contención (%)", "Contención", resultados,
                ResultadoSimulacionDto::getPorcentajeContencion, 520, 230));
        doc.add(barChart("R0 estimado", "R0", resultados,
                ResultadoSimulacionDto::getR0Estimado, 520, 230));
    }

    /** Pequeña leyenda horizontal con un cuadrito de color por estrategia. */
    private PdfPTable crearLeyendaEstrategias(List<ResultadoSimulacionDto> resultados) {
        Font fLey = FontFactory.getFont(FontFactory.HELVETICA, 9, COLOR_BODY);
        PdfPTable leyenda = new PdfPTable(resultados.size() * 2);
        try {
            leyenda.setWidthPercentage(100);
        } catch (Exception ignored) {}
        leyenda.setSpacingAfter(8);

        for (int i = 0; i < resultados.size(); i++) {
            PdfPCell c = new PdfPCell();
            c.setBackgroundColor(PALETA[i % PALETA.length]);
            c.setBorder(0);
            c.setFixedHeight(10);
            leyenda.addCell(c);

            PdfPCell t = new PdfPCell(new Phrase(
                    " " + resultados.get(i).getEstrategia().name(), fLey));
            t.setBorder(0);
            t.setVerticalAlignment(Element.ALIGN_MIDDLE);
            leyenda.addCell(t);
        }
        return leyenda;
    }

    private com.lowagie.text.Image barChart(String titulo, String etiquetaMetrica,
                                            List<ResultadoSimulacionDto> resultados,
                                            java.util.function.Function<ResultadoSimulacionDto, Double> extractor,
                                            int w, int h) throws Exception {
        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        for (ResultadoSimulacionDto r : resultados) {
            ds.addValue(extractor.apply(r), etiquetaMetrica, r.getEstrategia().name());
        }
        JFreeChart chart = ChartFactory.createBarChart(
            titulo, "", etiquetaMetrica, ds,
            PlotOrientation.VERTICAL, false, true, false);

        aplicarEstiloBarras(chart);
        return jfreechartAImagen(chart, w, h);
    }

    // ── Desglose del score ────────────────────────────────────────────────────

    private void agregarDesgloseScore(Document doc, List<ResultadoSimulacionDto> resultados) throws Exception {
        Font fH2  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, COLOR_TITULO);
        Font fTxt = FontFactory.getFont(FontFactory.HELVETICA, 10, COLOR_BODY);

        Paragraph h = new Paragraph("Desglose del score compuesto", fH2);
        h.setSpacingAfter(8);
        doc.add(h);

        Paragraph desc = new Paragraph(String.format(
            "Score = %.2f·pico + %.2f·duración + %.2f·afectados + %.2f·contención + %.2f·R0%n"
          + "Cada métrica se normaliza a [0,1]; las que son 'menor es mejor' se invierten.%n"
          + "El score final mide la calidad global de la estrategia (mayor = mejor).",
          AnalisisComparativo.W_PICO,
          AnalisisComparativo.W_DURACION,
          AnalisisComparativo.W_AFECTADOS,
          AnalisisComparativo.W_CONTENCION,
          AnalisisComparativo.W_R0), fTxt);
        desc.setSpacingAfter(12);
        doc.add(desc);

        List<ScoreEstrategia> ranking = new AnalisisComparativo().calcularRanking(resultados);

        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        for (ScoreEstrategia se : ranking) {
            for (Map.Entry<String, Double> e : se.componentes.entrySet()) {
                ds.addValue(e.getValue(), e.getKey(), se.estrategia.name());
            }
        }

        JFreeChart chart = ChartFactory.createStackedBarChart(
            "Score compuesto por estrategia (apilado)",
            "Estrategia", "Score parcial",
            ds, PlotOrientation.VERTICAL, true, true, false);

        aplicarEstiloApilado(chart);

        doc.add(jfreechartAImagen(chart, 520, 340));
    }

    // ── Estilos de gráfico (helpers) ──────────────────────────────────────────

    private void aplicarEstiloBarras(JFreeChart chart) {
        estilizarBase(chart);

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(COLOR_FONDO);
        plot.setRangeGridlinePaint(COLOR_GRID);
        plot.setRangeGridlineStroke(new BasicStroke(0.6f));
        plot.setDomainGridlinesVisible(false);
        plot.setOutlineVisible(false);
        plot.setInsets(new RectangleInsets(8, 8, 4, 16));

        BarRenderer rend = new BarRenderer() {
            @Override
            public Paint getItemPaint(int row, int column) {
                return PALETA[column % PALETA.length];
            }
        };
        rend.setBarPainter(new StandardBarPainter());
        rend.setShadowVisible(false);
        rend.setDrawBarOutline(false);
        rend.setMaximumBarWidth(0.13);
        rend.setDefaultItemLabelsVisible(true);
        rend.setDefaultItemLabelGenerator(new StandardCategoryItemLabelGenerator());
        rend.setDefaultItemLabelFont(FUENTE_LABEL_BARRA);
        rend.setDefaultItemLabelPaint(COLOR_BODY);
        rend.setDefaultPositiveItemLabelPosition(new ItemLabelPosition(
                ItemLabelAnchor.OUTSIDE12, TextAnchor.BOTTOM_CENTER));
        plot.setRenderer(rend);

        estilizarEjeCategoria(plot.getDomainAxis());
        estilizarEjeNumerico((NumberAxis) plot.getRangeAxis());
        ((NumberAxis) plot.getRangeAxis()).setUpperMargin(0.20);
    }

    private void aplicarEstiloApilado(JFreeChart chart) {
        estilizarBase(chart);

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(COLOR_FONDO);
        plot.setRangeGridlinePaint(COLOR_GRID);
        plot.setDomainGridlinesVisible(false);
        plot.setOutlineVisible(false);

        BarRenderer rend = (BarRenderer) plot.getRenderer();
        rend.setBarPainter(new StandardBarPainter());
        rend.setShadowVisible(false);
        rend.setDrawBarOutline(false);
        rend.setMaximumBarWidth(0.10);
        for (int i = 0; i < plot.getDataset().getRowCount(); i++) {
            rend.setSeriesPaint(i, PALETA[i % PALETA.length]);
        }

        estilizarEjeCategoria(plot.getDomainAxis());
        estilizarEjeNumerico((NumberAxis) plot.getRangeAxis());

        estilizarLeyenda(chart.getLegend());
    }

    private void aplicarEstiloXY(JFreeChart chart, boolean colorPorEstrategia) {
        estilizarBase(chart);

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(COLOR_FONDO);
        plot.setRangeGridlinePaint(COLOR_GRID);
        plot.setDomainGridlinePaint(COLOR_GRID);
        plot.setOutlineVisible(false);
        plot.setInsets(new RectangleInsets(8, 8, 4, 16));

        XYLineAndShapeRenderer rend = new XYLineAndShapeRenderer(true, false);
        for (int i = 0; i < plot.getSeriesCount(); i++) {
            if (colorPorEstrategia) {
                rend.setSeriesPaint(i, PALETA[i % PALETA.length]);
            }
            rend.setSeriesStroke(i, new BasicStroke(2.2f));
        }
        plot.setRenderer(rend);

        plot.getDomainAxis().setTickLabelFont(FUENTE_EJE);
        plot.getDomainAxis().setTickLabelPaint(COLOR_BODY);
        plot.getDomainAxis().setLabelFont(FUENTE_EJE_TITULO);
        plot.getDomainAxis().setLabelPaint(COLOR_SUAVE);
        plot.getDomainAxis().setAxisLinePaint(COLOR_AXIS);
        plot.getDomainAxis().setTickMarkPaint(COLOR_AXIS);

        plot.getRangeAxis().setTickLabelFont(FUENTE_EJE);
        plot.getRangeAxis().setTickLabelPaint(COLOR_BODY);
        plot.getRangeAxis().setLabelFont(FUENTE_EJE_TITULO);
        plot.getRangeAxis().setLabelPaint(COLOR_SUAVE);
        plot.getRangeAxis().setAxisLinePaint(COLOR_AXIS);
        plot.getRangeAxis().setTickMarkPaint(COLOR_AXIS);

        estilizarLeyenda(chart.getLegend());
    }

    private void estilizarBase(JFreeChart chart) {
        chart.setBackgroundPaint(Color.WHITE);
        chart.setBorderVisible(false);
        chart.setPadding(new RectangleInsets(8, 4, 4, 4));
        if (chart.getTitle() != null) {
            chart.getTitle().setFont(FUENTE_TITULO_GRAFICO);
            chart.getTitle().setPaint(COLOR_TITULO);
            chart.getTitle().setMargin(new RectangleInsets(4, 4, 12, 4));
        }
    }

    private void estilizarEjeCategoria(CategoryAxis xAxis) {
        xAxis.setCategoryLabelPositions(CategoryLabelPositions.DOWN_45);
        xAxis.setTickLabelFont(FUENTE_EJE);
        xAxis.setTickLabelPaint(COLOR_BODY);
        xAxis.setLabelFont(FUENTE_EJE_TITULO);
        xAxis.setLabelPaint(COLOR_SUAVE);
        xAxis.setAxisLinePaint(COLOR_AXIS);
        xAxis.setTickMarkPaint(COLOR_AXIS);
        xAxis.setCategoryMargin(0.30);
    }

    private void estilizarEjeNumerico(NumberAxis yAxis) {
        yAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        yAxis.setTickLabelFont(FUENTE_EJE);
        yAxis.setTickLabelPaint(COLOR_BODY);
        yAxis.setLabelFont(FUENTE_EJE_TITULO);
        yAxis.setLabelPaint(COLOR_SUAVE);
        yAxis.setAxisLinePaint(COLOR_AXIS);
        yAxis.setTickMarkPaint(COLOR_AXIS);
    }

    private void estilizarLeyenda(LegendTitle legend) {
        if (legend == null) return;
        legend.setItemFont(FUENTE_LEYENDA);
        legend.setItemPaint(COLOR_BODY);
        legend.setBackgroundPaint(Color.WHITE);
        legend.setFrame(BlockBorder.NONE);
        legend.setMargin(new RectangleInsets(4, 4, 4, 4));
    }

    // ── Glosario de métricas ──────────────────────────────────────────────────

    private void agregarGlosario(Document doc) throws Exception {
        Font fH2    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, COLOR_TITULO);
        Font fIntro = FontFactory.getFont(FontFactory.HELVETICA, 10, COLOR_BODY);
        Font fEnc   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, Color.WHITE);
        Font fVar   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, COLOR_TITULO);
        Font fDesc  = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, COLOR_BODY);
        Font fBien  = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9.5f, new Color(0x166534));

        Paragraph titulo = new Paragraph("Glosario de métricas — Guía de interpretación", fH2);
        titulo.setSpacingAfter(6);
        doc.add(titulo);

        Paragraph intro = new Paragraph(
            "Esta sección explica cada variable que aparece en el informe, qué representa "
          + "dentro del modelo epidémico SIRV y qué valores se consideran favorables desde "
          + "el punto de vista de salud pública.", fIntro);
        intro.setSpacingAfter(12);
        doc.add(intro);

        PdfPTable tabla = new PdfPTable(new float[]{1.4f, 2.8f, 2.0f});
        tabla.setWidthPercentage(100);
        tabla.setSpacingAfter(14);

        for (String cab : new String[]{"Variable", "Qué mide", "Resultado favorable"}) {
            PdfPCell c = new PdfPCell(new Phrase(cab, fEnc));
            c.setBackgroundColor(COLOR_TBL_HEAD);
            c.setBorderColor(COLOR_TBL_HEAD);
            c.setPadding(7);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            tabla.addCell(c);
        }

        Color[] bg = {Color.WHITE, COLOR_ZEBRA};
        int fila = 0;
        String[][] filas = {
            {"S — Susceptibles",
             "Personas que aún no se han infectado y pueden contagiarse. Al inicio son la mayoría de la población.",
             "Alto al final del brote: indica que la estrategia protegió a más personas."},
            {"I — Infectados",
             "Personas actualmente enfermas y capaces de contagiar a otros. La curva I(t) muestra cómo evoluciona el brote.",
             "Curva baja y estrecha: pico pequeño y brote corto."},
            {"R — Recuperados\n(Afectados totales)",
             "Personas que se infectaron y se recuperaron. El valor final de R al terminar el brote equivale al total de afectados.",
             "Número bajo: cuantos menos hayan pasado por R, mejor."},
            {"V — Vacunados",
             "Personas inmunizadas mediante vacunación antes de infectarse. No contribuyen a la cadena de contagio.",
             "Número alto: mayor cobertura de vacunación lograda."},
            {"Pico máximo\nde infectados",
             "Mayor número de personas enfermas simultáneamente en cualquier turno. Mide la presión sobre el sistema de salud.",
             "Menor = mejor. Un pico bajo evita el colapso hospitalario."},
            {"t-Pico\n(turno del pico)",
             "Turno en que se alcanzó el máximo de infectados. Un pico tardío indica que el brote tardó en estallar.",
             "Mayor = mejor. Da más tiempo para vacunar y preparar el sistema."},
            {"Duración del brote",
             "Número de turnos desde el inicio hasta que no quedan infectados activos en la red.",
             "Menor = mejor. Un brote corto minimiza el tiempo de exposición."},
            {"Total de afectados",
             "Cuántas personas se infectaron durante toda la simulación. Coincide con R al final del brote.",
             "Menor = mejor. Refleja cuántas personas realmente enfermaron."},
            {"Contención %",
             "Porcentaje de la población que NO se infectó. Fórmula: 100 % − (afectados / población) × 100.",
             "Mayor = mejor. Cercano a 100 % significa que casi nadie se enfermó."},
            {"R0 estimado",
             "Número promedio de personas que contagia una persona infectada al inicio del brote. R0 < 1: el brote se extingue; R0 > 1: crecimiento exponencial.",
             "Menor = mejor. R0 idealmente por debajo de 1."},
            {"Score compuesto\n[0 – 1]",
             "Indicador global que combina las cinco métricas con pesos: 30 % pico + 25 % afectados + 20 % contención + 15 % duración + 10 % R0. Cada métrica se normaliza; las de \"menor es mejor\" se invierten.",
             "Mayor = mejor. 1,000 representaría la estrategia ideal en todos los frentes."},
        };

        for (String[] f : filas) {
            Color bgFila = bg[fila % 2];
            PdfPCell cVar = new PdfPCell(new Phrase(f[0], fVar));
            cVar.setBackgroundColor(bgFila);
            cVar.setBorderColor(new Color(0xeceff3));
            cVar.setPadding(6);
            tabla.addCell(cVar);

            PdfPCell cDesc = new PdfPCell(new Phrase(f[1], fDesc));
            cDesc.setBackgroundColor(bgFila);
            cDesc.setBorderColor(new Color(0xeceff3));
            cDesc.setPadding(6);
            tabla.addCell(cDesc);

            PdfPCell cBien = new PdfPCell(new Phrase(f[2], fBien));
            cBien.setBackgroundColor(bgFila);
            cBien.setBorderColor(new Color(0xeceff3));
            cBien.setPadding(6);
            tabla.addCell(cBien);
            fila++;
        }
        doc.add(tabla);

        Paragraph nota = new Paragraph(
            "Nota: el modelo SIRV es una simulación discreta sobre grafos — los resultados dependen "
          + "de la topología de la red (quién está conectado con quién). Por eso una misma estrategia "
          + "puede comportarse distinto en redes distintas, algo que el modo de experimento por lotes "
          + "captura promediando múltiples grafos independientes.",
            FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, COLOR_SUAVE));
        doc.add(nota);
    }

    // ── Conversión a imagen embebible en PDF ──────────────────────────────────

    private com.lowagie.text.Image jfreechartAImagen(JFreeChart chart, int w, int h) throws Exception {
        java.awt.image.BufferedImage img = chart.createBufferedImage(w, h);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        com.lowagie.text.Image image = com.lowagie.text.Image.getInstance(baos.toByteArray());
        image.setAlignment(Element.ALIGN_CENTER);
        image.scaleToFit(520, h);
        return image;
    }
}
