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
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
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

    private static final Color[] PALETA = {
        new Color(0x3498db), new Color(0xe74c3c), new Color(0x2ecc71),
        new Color(0xf39c12), new Color(0x9b59b6), new Color(0x1abc9c)
    };

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

            doc.close();
        } catch (Exception e) {
            throw new IOException("Error generando PDF: " + e.getMessage(), e);
        }
    }

    // ── Páginas del reporte ───────────────────────────────────────────────────

    private void agregarPortada(Document doc, List<ResultadoSimulacionDto> resultados) throws Exception {
        Font fTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, new Color(0x2c3e50));
        Font fSub    = FontFactory.getFont(FontFactory.HELVETICA, 12, new Color(0x7f8c8d));
        Font fNormal = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.BLACK);

        Paragraph titulo = new Paragraph("Reporte de Simulación Epidémica SIRV", fTitulo);
        titulo.setAlignment(Element.ALIGN_CENTER);
        titulo.setSpacingAfter(8);
        doc.add(titulo);

        Paragraph subtitulo = new Paragraph(
            "Comparativo de Estrategias de Vacunación — Universidad Industrial de Santander", fSub);
        subtitulo.setAlignment(Element.ALIGN_CENTER);
        subtitulo.setSpacingAfter(18);
        doc.add(subtitulo);

        String fecha = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        int totalPob = resultados.get(0).getTotalPoblacion();

        Paragraph contexto = new Paragraph(String.format(
            "Generado: %s%nTamaño de la red: %d personas%nEstrategias evaluadas: %d",
            fecha, totalPob, resultados.size()), fNormal);
        contexto.setSpacingAfter(14);
        doc.add(contexto);
    }

    private void agregarTablaResumen(Document doc, List<ResultadoSimulacionDto> resultados) throws Exception {
        Font fEnc   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE);
        Font fCelda = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
        Font fH2    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(0x2c3e50));

        Paragraph h = new Paragraph("Tabla resumen de métricas", fH2);
        h.setSpacingBefore(6);
        h.setSpacingAfter(8);
        doc.add(h);

        PdfPTable tabla = new PdfPTable(new float[]{2.2f, 1f, 1f, 1.1f, 1.2f, 1.2f, 1f});
        tabla.setWidthPercentage(100);
        String[] cabeceras = {"Estrategia", "Pico", "t-Pico", "Duración", "Afectados", "Contención%", "R0"};
        for (String c : cabeceras) {
            PdfPCell celda = new PdfPCell(new Phrase(c, fEnc));
            celda.setBackgroundColor(new Color(0x34495e));
            celda.setHorizontalAlignment(Element.ALIGN_CENTER);
            celda.setPadding(6);
            tabla.addCell(celda);
        }

        boolean alt = false;
        for (ResultadoSimulacionDto r : resultados) {
            Color bg = alt ? new Color(0xf6f7f9) : Color.WHITE;
            alt = !alt;
            addCelda(tabla, r.getEstrategia().name(),                            fCelda, bg, Element.ALIGN_LEFT);
            addCelda(tabla, String.valueOf(r.getPicoMaximoInfectados()),         fCelda, bg, Element.ALIGN_CENTER);
            addCelda(tabla, String.valueOf(r.getTurnoDePico()),                  fCelda, bg, Element.ALIGN_CENTER);
            addCelda(tabla, String.valueOf(r.getDuracionBrote()),                fCelda, bg, Element.ALIGN_CENTER);
            addCelda(tabla, String.valueOf(r.getTotalRecuperados()),             fCelda, bg, Element.ALIGN_CENTER);
            addCelda(tabla, String.format("%.1f%%", r.getPorcentajeContencion()),fCelda, bg, Element.ALIGN_CENTER);
            addCelda(tabla, String.format("%.2f",  r.getR0Estimado()),           fCelda, bg, Element.ALIGN_CENTER);
        }
        doc.add(tabla);
    }

    private void addCelda(PdfPTable tabla, String texto, Font font, Color bg, int align) {
        PdfPCell c = new PdfPCell(new Phrase(texto, font));
        c.setBackgroundColor(bg);
        c.setHorizontalAlignment(align);
        c.setPadding(5);
        tabla.addCell(c);
    }

    private void agregarAnalisisGanador(Document doc, List<ResultadoSimulacionDto> resultados) throws Exception {
        Font fH2  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(0x2c3e50));
        Font fJus = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.BLACK);
        Font fGan = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, new Color(0x27ae60));

        AnalisisComparativo analisis = new AnalisisComparativo();
        List<ScoreEstrategia> ranking = analisis.calcularRanking(resultados);
        if (ranking.isEmpty()) return;

        ScoreEstrategia ganador = ranking.get(0);

        Paragraph h = new Paragraph("Estrategia óptima identificada", fH2);
        h.setSpacingBefore(16);
        h.setSpacingAfter(8);
        doc.add(h);

        Paragraph gan = new Paragraph(String.format(
            "★ %s  —  score compuesto %.3f / 1.000", ganador.estrategia, ganador.score), fGan);
        gan.setSpacingAfter(8);
        doc.add(gan);

        Paragraph jus = new Paragraph(analisis.justificarGanador(ranking, resultados), fJus);
        jus.setSpacingAfter(6);
        doc.add(jus);
    }

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
        estilizarLineas(chart);
        doc.add(jfreechartAImagen(chart, 520, 320));
    }

    private void agregarCurvasSIRVPorEstrategia(Document doc, List<ResultadoSimulacionDto> resultados) throws Exception {
        Font fH2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(0x2c3e50));
        Paragraph h = new Paragraph("Curvas SIRV por estrategia", fH2);
        h.setSpacingAfter(8);
        doc.add(h);

        for (ResultadoSimulacionDto r : resultados) {
            XYSeriesCollection ds = new XYSeriesCollection();
            XYSeries sS = new XYSeries("S");
            XYSeries sI = new XYSeries("I");
            XYSeries sR = new XYSeries("R");
            XYSeries sV = new XYSeries("V");
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

            XYPlot plot = chart.getXYPlot();
            plot.setBackgroundPaint(Color.WHITE);
            plot.setRangeGridlinePaint(new Color(0xe0e0e0));
            plot.setDomainGridlinePaint(new Color(0xe0e0e0));

            XYLineAndShapeRenderer rend = new XYLineAndShapeRenderer(true, false);
            rend.setSeriesPaint(0, new Color(0x3498db)); // S azul
            rend.setSeriesPaint(1, new Color(0xe74c3c)); // I rojo
            rend.setSeriesPaint(2, new Color(0x2ecc71)); // R verde
            rend.setSeriesPaint(3, new Color(0xf39c12)); // V naranja
            plot.setRenderer(rend);

            doc.add(jfreechartAImagen(chart, 520, 200));
        }
    }

    private void agregarBarrasMetricas(Document doc, List<ResultadoSimulacionDto> resultados) throws Exception {
        Font fH2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(0x2c3e50));
        Paragraph h = new Paragraph("Comparativo por métrica", fH2);
        h.setSpacingAfter(8);
        doc.add(h);

        doc.add(barChart("Pico máximo de infectados", "Pico", resultados,
                r -> (double) r.getPicoMaximoInfectados(), 520, 200));
        doc.add(barChart("Duración del brote (turnos)", "Duración", resultados,
                r -> (double) r.getDuracionBrote(), 520, 200));
        doc.add(barChart("Total de afectados", "Afectados", resultados,
                r -> (double) r.getTotalRecuperados(), 520, 200));
        doc.add(barChart("Porcentaje de contención (%)", "Contención", resultados,
                ResultadoSimulacionDto::getPorcentajeContencion, 520, 200));
        doc.add(barChart("R0 estimado", "R0", resultados,
                ResultadoSimulacionDto::getR0Estimado, 520, 200));
    }

    private void agregarDesgloseScore(Document doc, List<ResultadoSimulacionDto> resultados) throws Exception {
        Font fH2  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(0x2c3e50));
        Font fTxt = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);

        Paragraph h = new Paragraph("Desglose del score compuesto", fH2);
        h.setSpacingAfter(6);
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
        desc.setSpacingAfter(10);
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

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(new Color(0xe0e0e0));
        BarRenderer rend = (BarRenderer) plot.getRenderer();
        rend.setBarPainter(new StandardBarPainter());
        rend.setShadowVisible(false);
        for (int i = 0; i < ds.getRowCount(); i++) {
            rend.setSeriesPaint(i, PALETA[i % PALETA.length]);
        }

        doc.add(jfreechartAImagen(chart, 520, 320));
    }

    // ── Helpers de gráficos ───────────────────────────────────────────────────

    private com.lowagie.text.Image barChart(String titulo, String etiquetaMetrica,
                                            List<ResultadoSimulacionDto> resultados,
                                            java.util.function.Function<ResultadoSimulacionDto, Double> extractor,
                                            int w, int h) throws Exception {
        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        for (ResultadoSimulacionDto r : resultados) {
            ds.addValue(extractor.apply(r), etiquetaMetrica, r.getEstrategia().name());
        }
        JFreeChart chart = ChartFactory.createBarChart(
            titulo, "Estrategia", etiquetaMetrica, ds,
            PlotOrientation.VERTICAL, false, true, false);

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(new Color(0xe0e0e0));
        BarRenderer rend = (BarRenderer) plot.getRenderer();
        rend.setBarPainter(new StandardBarPainter());
        rend.setShadowVisible(false);
        for (int i = 0; i < resultados.size(); i++) {
            rend.setSeriesPaint(i, PALETA[i % PALETA.length]);
        }
        CategoryAxis xAxis = plot.getDomainAxis();
        xAxis.setCategoryLabelPositions(org.jfree.chart.axis.CategoryLabelPositions.UP_45);
        NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
        yAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        return jfreechartAImagen(chart, w, h);
    }

    private void estilizarLineas(JFreeChart chart) {
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(new Color(0xe0e0e0));
        plot.setDomainGridlinePaint(new Color(0xe0e0e0));

        XYLineAndShapeRenderer rend = new XYLineAndShapeRenderer(true, false);
        for (int i = 0; i < plot.getSeriesCount(); i++) {
            rend.setSeriesPaint(i, PALETA[i % PALETA.length]);
            rend.setSeriesStroke(i, new java.awt.BasicStroke(2.0f));
        }
        plot.setRenderer(rend);
    }

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
