package infrastructure.persistence;

import application.dto.ResultadoLoteDto;
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
import domain.value.EstrategiaVacunacion;
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
 * Genera un reporte PDF DEDICADO al experimento por lotes.
 *
 * A diferencia de {@link GeneradorReportePDF} (una sola corrida por estrategia),
 * esta plantilla deja explícito que cada métrica es el PROMEDIO de las N corridas
 * (N grafos distintos e independientes por estrategia) e incorpora el análisis
 * ACUMULADO de victorias: en cuántas corridas cada estrategia obtuvo el mejor
 * score compuesto.
 *
 * Estructura del PDF:
 *   1. Portada dedicada al estudio por lotes (promedios)
 *   2. Tabla resumen de promedios + victorias acumuladas, con ranking por score
 *   3. Estrategia óptima identificada (promedio + acumulado)
 *   4. Gráfico de victorias acumuladas
 *   5. Curva I(t) promedio comparativa
 *   6. Curvas SIRV promedio por estrategia
 *   7. Comparativas por métrica (promedio)
 *   8. Score compuesto con desglose por componente
 */
public class GeneradorReporteLotePDF {

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
    private static final Color COLOR_ACENTO    = new Color(0x6d28d9);   // morado: distingue el reporte de lote
    private static final Color COLOR_ACENTO_T  = new Color(0xe9defb);
    private static final Color COLOR_GANA_BG   = new Color(0xdcfce7);
    private static final Color COLOR_GANA_TXT  = new Color(0x166534);
    private static final Color COLOR_ZEBRA     = new Color(0xf6f7f9);
    private static final Color COLOR_TBL_HEAD  = new Color(0x6d28d9);

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

    private final AnalisisComparativo analisis = new AnalisisComparativo();

    // ── API pública ───────────────────────────────────────────────────────────

    public void exportar(ResultadoLoteDto lote, String rutaPdf) throws IOException {
        if (lote == null || lote.getPromedios() == null || lote.getPromedios().isEmpty()) {
            throw new IOException("Sin resultados de lote para exportar");
        }

        List<ResultadoSimulacionDto> promedios = lote.getPromedios();

        Document doc = new Document(PageSize.A4, 36, 36, 50, 50);
        try (FileOutputStream fos = new FileOutputStream(rutaPdf)) {
            PdfWriter.getInstance(doc, fos);
            doc.open();

            agregarPortada(doc, lote);
            agregarTablaResumen(doc, lote);
            agregarAnalisisGanador(doc, lote);
            doc.newPage();

            agregarGraficoVictorias(doc, lote);
            doc.newPage();

            agregarGraficoCurvaIComparativa(doc, promedios);
            doc.newPage();

            agregarCurvasSIRVPorEstrategia(doc, promedios);
            doc.newPage();

            agregarBarrasMetricas(doc, promedios);
            doc.newPage();

            agregarDesgloseScore(doc, promedios);
            doc.newPage();

            agregarGlosario(doc, true);

            doc.close();
        } catch (Exception e) {
            throw new IOException("Error generando PDF de lote: " + e.getMessage(), e);
        }
    }

    // ── Portada ───────────────────────────────────────────────────────────────

    private void agregarPortada(Document doc, ResultadoLoteDto lote) throws Exception {
        List<ResultadoSimulacionDto> promedios = lote.getPromedios();

        Font fHeroTit = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Color.WHITE);
        Font fHeroSub = FontFactory.getFont(FontFactory.HELVETICA, 12, COLOR_ACENTO_T);

        PdfPTable banner = new PdfPTable(1);
        banner.setWidthPercentage(100);
        banner.setSpacingAfter(24);

        PdfPCell heroCell = new PdfPCell();
        heroCell.setBackgroundColor(COLOR_ACENTO);
        heroCell.setBorder(0);
        heroCell.setPadding(22);

        Paragraph pT = new Paragraph("Reporte de Experimento por Lotes — SIRV", fHeroTit);
        pT.setAlignment(Element.ALIGN_CENTER);
        heroCell.addElement(pT);

        Paragraph pS = new Paragraph(
                "Estudio estadístico de estrategias de vacunación sobre grafos independientes "
              + "— Universidad Industrial de Santander",
                fHeroSub);
        pS.setAlignment(Element.ALIGN_CENTER);
        heroCell.addElement(pS);

        banner.addCell(heroCell);
        doc.add(banner);

        Font fLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, COLOR_SUAVE);
        Font fValor = FontFactory.getFont(FontFactory.HELVETICA, 12, COLOR_BODY);

        PdfPTable meta = new PdfPTable(new float[]{1.2f, 2f});
        meta.setWidthPercentage(85);
        meta.setHorizontalAlignment(Element.ALIGN_CENTER);
        meta.setSpacingAfter(14);

        String fecha = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        int totalPob = promedios.get(0).getTotalPoblacion();

        agregarMetaFila(meta, "Generado",                fecha,                                          fLabel, fValor);
        agregarMetaFila(meta, "Estrategias evaluadas",   String.valueOf(promedios.size()),               fLabel, fValor);
        agregarMetaFila(meta, "Grafos por estrategia",   String.valueOf(lote.getNGrafosPorEstrategia()), fLabel, fValor);
        agregarMetaFila(meta, "Simulaciones totales",    lote.getTotalSimulaciones() + " (grafos independientes)", fLabel, fValor);
        agregarMetaFila(meta, "Tamaño de la red",        totalPob + " personas (aprox.)",                fLabel, fValor);
        doc.add(meta);

        // Aviso destacado: todas las métricas son promedios
        Font fAviso = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, COLOR_ACENTO);
        PdfPTable caja = new PdfPTable(1);
        caja.setWidthPercentage(85);
        caja.setHorizontalAlignment(Element.ALIGN_CENTER);
        PdfPCell c = new PdfPCell(new Phrase(
                "Todas las métricas de este informe son el PROMEDIO de las "
              + lote.getNGrafosPorEstrategia() + " corridas de cada estrategia. "
              + "Las victorias son la lectura ACUMULADA: en cuántas corridas la estrategia "
              + "obtuvo el mejor score compuesto.", fAviso));
        c.setBackgroundColor(COLOR_ACENTO_T);
        c.setBorderColor(COLOR_ACENTO);
        c.setPadding(10);
        caja.addCell(c);
        doc.add(caja);
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

    // ── Tabla resumen (promedios + victorias) ──────────────────────────────────

    private void agregarTablaResumen(Document doc, ResultadoLoteDto lote) throws Exception {
        List<ResultadoSimulacionDto> promedios = lote.getPromedios();
        Map<EstrategiaVacunacion, Integer> victorias = lote.getVictorias();
        int n = lote.getNGrafosPorEstrategia();

        Font fEnc   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, Color.WHITE);
        Font fCelda = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, COLOR_BODY);
        Font fGana  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, COLOR_GANA_TXT);
        Font fH2    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, COLOR_TITULO);

        Paragraph h = new Paragraph("Tabla resumen — promedios por estrategia", fH2);
        h.setSpacingBefore(14);
        h.setSpacingAfter(10);
        doc.add(h);

        PdfPTable tabla = new PdfPTable(new float[]{0.35f, 2.2f, 1f, 1f, 1.1f, 1.2f, 1.2f, 0.9f, 1.1f});
        tabla.setWidthPercentage(100);
        tabla.setSpacingAfter(6);

        String[] cabeceras = {"", "Estrategia", "Pico", "t-Pico", "Duración",
                              "Afectados", "Contención%", "R0", "Victorias"};
        for (String c : cabeceras) {
            PdfPCell celda = new PdfPCell(new Phrase(c, fEnc));
            celda.setBackgroundColor(COLOR_TBL_HEAD);
            celda.setBorderColor(COLOR_TBL_HEAD);
            celda.setHorizontalAlignment(Element.ALIGN_CENTER);
            celda.setPadding(7);
            tabla.addCell(celda);
        }

        List<ScoreEstrategia> ranking = analisis.calcularRanking(promedios);
        EstrategiaVacunacion ganador = ranking.isEmpty() ? null : ranking.get(0).estrategia;

        boolean alt = false;
        for (ScoreEstrategia s : ranking) {
            ResultadoSimulacionDto r = porEstrategia(promedios, s.estrategia);
            if (r == null) continue;
            boolean esGanador = r.getEstrategia() == ganador;
            Color bg = esGanador ? COLOR_GANA_BG : (alt ? COLOR_ZEBRA : Color.WHITE);
            Font fil = esGanador ? fGana : fCelda;
            alt = !alt;

            PdfPCell color = new PdfPCell(new Phrase(" "));
            color.setBackgroundColor(colorDeEstrategia(promedios, r.getEstrategia()));
            color.setBorderColor(bg);
            color.setFixedHeight(20);
            tabla.addCell(color);

            String nombre = (esGanador ? "* " : "  ") + r.getEstrategia().name();
            agregarCelda(tabla, nombre,                                              fil, bg, Element.ALIGN_LEFT);
            agregarCelda(tabla, String.valueOf(r.getPicoMaximoInfectados()),         fil, bg, Element.ALIGN_CENTER);
            agregarCelda(tabla, String.valueOf(r.getTurnoDePico()),                  fil, bg, Element.ALIGN_CENTER);
            agregarCelda(tabla, String.valueOf(r.getDuracionBrote()),                fil, bg, Element.ALIGN_CENTER);
            agregarCelda(tabla, String.valueOf(r.getTotalRecuperados()),             fil, bg, Element.ALIGN_CENTER);
            agregarCelda(tabla, String.format("%.1f%%", r.getPorcentajeContencion()), fil, bg, Element.ALIGN_CENTER);
            agregarCelda(tabla, String.format("%.2f",  r.getR0Estimado()),           fil, bg, Element.ALIGN_CENTER);
            agregarCelda(tabla, victorias.getOrDefault(r.getEstrategia(), 0) + " / " + n,
                                                                                     fil, bg, Element.ALIGN_CENTER);
        }
        doc.add(tabla);

        Paragraph nota = new Paragraph(
                "* fila destacada = mejor score compuesto promedio. "
              + "Métricas = promedio de las N corridas; Victorias = corridas ganadas (acumulado).",
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

    private void agregarAnalisisGanador(Document doc, ResultadoLoteDto lote) throws Exception {
        List<ResultadoSimulacionDto> promedios = lote.getPromedios();
        Map<EstrategiaVacunacion, Integer> victorias = lote.getVictorias();

        Font fH2  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, COLOR_TITULO);
        Font fJus = FontFactory.getFont(FontFactory.HELVETICA, 11, COLOR_BODY);
        Font fGan = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, new Color(0x166534));

        List<ScoreEstrategia> ranking = analisis.calcularRanking(promedios);
        if (ranking.isEmpty()) return;
        ScoreEstrategia ganador = ranking.get(0);

        Paragraph h = new Paragraph("Estrategia óptima identificada (promedio + acumulado)", fH2);
        h.setSpacingBefore(14);
        h.setSpacingAfter(8);
        doc.add(h);

        Paragraph gan = new Paragraph(String.format(
            "* %s  —  score compuesto promedio %.3f / 1.000  ·  %d de %d corridas ganadas",
            ganador.estrategia, ganador.score,
            victorias.getOrDefault(ganador.estrategia, 0), lote.getNGrafosPorEstrategia()), fGan);
        gan.setSpacingAfter(6);
        doc.add(gan);

        Paragraph jus = new Paragraph(analisis.justificarGanador(ranking, promedios), fJus);
        jus.setSpacingAfter(6);
        doc.add(jus);
    }

    // ── Gráfico de victorias acumuladas ─────────────────────────────────────────

    private void agregarGraficoVictorias(Document doc, ResultadoLoteDto lote) throws Exception {
        Font fH2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, COLOR_TITULO);
        Paragraph h = new Paragraph("Victorias acumuladas por estrategia", fH2);
        h.setSpacingAfter(8);
        doc.add(h);

        Font fTxt = FontFactory.getFont(FontFactory.HELVETICA, 10, COLOR_BODY);
        Paragraph desc = new Paragraph(String.format(
            "En cada una de las %d corridas las %d estrategias se comparan por su score "
          + "compuesto; la barra indica en cuántas corridas cada estrategia quedó primera.",
            lote.getNGrafosPorEstrategia(), lote.getPromedios().size()), fTxt);
        desc.setSpacingAfter(10);
        doc.add(desc);

        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        for (ResultadoSimulacionDto r : lote.getPromedios()) {
            int v = lote.getVictorias().getOrDefault(r.getEstrategia(), 0);
            ds.addValue((double) v, "Victorias", r.getEstrategia().name());
        }
        JFreeChart chart = ChartFactory.createBarChart(
            "Corridas ganadas (de " + lote.getNGrafosPorEstrategia() + ")", "", "Victorias",
            ds, PlotOrientation.VERTICAL, false, true, false);
        aplicarEstiloBarras(chart);
        doc.add(jfreechartAImagen(chart, 520, 300));
    }

    // ── Curvas (sobre promedios) ────────────────────────────────────────────────

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
            "Curva de Infectados I(t) promedio por estrategia",
            "Turno", "Infectados activos (promedio)",
            dataset, PlotOrientation.VERTICAL, true, true, false);
        aplicarEstiloXY(chart, true);
        doc.add(jfreechartAImagen(chart, 520, 340));
    }

    private void agregarCurvasSIRVPorEstrategia(Document doc, List<ResultadoSimulacionDto> resultados) throws Exception {
        Font fH2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, COLOR_TITULO);
        Paragraph h = new Paragraph("Curvas SIRV promedio por estrategia", fH2);
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
                r.getEstrategia().name() + " (promedio)",
                "Turno", "Personas",
                ds, PlotOrientation.VERTICAL, true, true, false);

            aplicarEstiloXY(chart, false);
            XYPlot plot = chart.getXYPlot();
            XYLineAndShapeRenderer rend = (XYLineAndShapeRenderer) plot.getRenderer();
            rend.setSeriesPaint(0, new Color(0x3498db));
            rend.setSeriesPaint(1, new Color(0xe74c3c));
            rend.setSeriesPaint(2, new Color(0x2ecc71));
            rend.setSeriesPaint(3, new Color(0xf39c12));

            doc.add(jfreechartAImagen(chart, 520, 210));
        }
    }

    // ── Barras de métricas (promedio) ───────────────────────────────────────────

    private void agregarBarrasMetricas(Document doc, List<ResultadoSimulacionDto> resultados) throws Exception {
        Font fH2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, COLOR_TITULO);
        Paragraph h = new Paragraph("Comparativo por métrica (promedio del lote)", fH2);
        h.setSpacingAfter(8);
        doc.add(h);

        doc.add(crearLeyendaEstrategias(resultados));

        doc.add(barChart("Pico máximo de infectados (promedio)", "Pico", resultados,
                r -> (double) r.getPicoMaximoInfectados(), 520, 230));
        doc.add(barChart("Duración del brote (turnos, promedio)", "Duración", resultados,
                r -> (double) r.getDuracionBrote(), 520, 230));
        doc.add(barChart("Total de afectados (promedio)", "Afectados", resultados,
                r -> (double) r.getTotalRecuperados(), 520, 230));
        doc.add(barChart("Porcentaje de contención (%, promedio)", "Contención", resultados,
                ResultadoSimulacionDto::getPorcentajeContencion, 520, 230));
        doc.add(barChart("R0 estimado (promedio)", "R0", resultados,
                ResultadoSimulacionDto::getR0Estimado, 520, 230));
    }

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

    // ── Desglose del score ──────────────────────────────────────────────────────

    private void agregarDesgloseScore(Document doc, List<ResultadoSimulacionDto> resultados) throws Exception {
        Font fH2  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, COLOR_TITULO);
        Font fTxt = FontFactory.getFont(FontFactory.HELVETICA, 10, COLOR_BODY);

        Paragraph h = new Paragraph("Desglose del score compuesto (sobre promedios)", fH2);
        h.setSpacingAfter(8);
        doc.add(h);

        Paragraph desc = new Paragraph(String.format(
            "Score = %.2f·pico + %.2f·duración + %.2f·afectados + %.2f·contención + %.2f·R0%n"
          + "Cada métrica se normaliza a [0,1]; las que son 'menor es mejor' se invierten.%n"
          + "El score se calcula sobre las métricas promediadas del lote (mayor = mejor).",
          AnalisisComparativo.W_PICO,
          AnalisisComparativo.W_DURACION,
          AnalisisComparativo.W_AFECTADOS,
          AnalisisComparativo.W_CONTENCION,
          AnalisisComparativo.W_R0), fTxt);
        desc.setSpacingAfter(12);
        doc.add(desc);

        List<ScoreEstrategia> ranking = analisis.calcularRanking(resultados);

        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        for (ScoreEstrategia se : ranking) {
            for (Map.Entry<String, Double> e : se.componentes.entrySet()) {
                ds.addValue(e.getValue(), e.getKey(), se.estrategia.name());
            }
        }

        JFreeChart chart = ChartFactory.createStackedBarChart(
            "Score compuesto promedio por estrategia (apilado)",
            "Estrategia", "Score parcial",
            ds, PlotOrientation.VERTICAL, true, true, false);

        aplicarEstiloApilado(chart);
        doc.add(jfreechartAImagen(chart, 520, 340));
    }

    // ── Estilos de gráfico (helpers) ────────────────────────────────────────────

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

    // ── Glosario de métricas ────────────────────────────────────────────────────

    private void agregarGlosario(Document doc, boolean esLote) throws Exception {
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
          + "el punto de vista de salud pública. Las métricas de este informe son "
          + "PROMEDIOS de múltiples corridas independientes.", fIntro);
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
            {"Victorias\n(acumulado lote)",
             "En cuántas de las N corridas independientes esta estrategia obtuvo el mayor score compuesto. Mide la consistencia de la estrategia ante diferentes topologías de red.",
             "Mayor = mejor. Más victorias indican robustez frente a variaciones en la red."},
        };

        int totalFilas = esLote ? filas.length : filas.length - 1;
        for (int i = 0; i < totalFilas; i++) {
            String[] f = filas[i];
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
          + "puede comportarse distinto en redes distintas; el experimento por lotes captura esta "
          + "variabilidad promediando múltiples grafos independientes y contando victorias.",
            FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, COLOR_SUAVE));
        doc.add(nota);
    }

    // ── Conversión a imagen embebible en PDF ────────────────────────────────────

    private com.lowagie.text.Image jfreechartAImagen(JFreeChart chart, int w, int h) throws Exception {
        java.awt.image.BufferedImage img = chart.createBufferedImage(w, h);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        com.lowagie.text.Image image = com.lowagie.text.Image.getInstance(baos.toByteArray());
        image.setAlignment(Element.ALIGN_CENTER);
        image.scaleToFit(520, h);
        return image;
    }

    // ── Auxiliares ──────────────────────────────────────────────────────────────

    private ResultadoSimulacionDto porEstrategia(List<ResultadoSimulacionDto> lista, EstrategiaVacunacion e) {
        for (ResultadoSimulacionDto r : lista) {
            if (r.getEstrategia() == e) return r;
        }
        return null;
    }

    private Color colorDeEstrategia(List<ResultadoSimulacionDto> lista, EstrategiaVacunacion e) {
        for (int i = 0; i < lista.size(); i++) {
            if (lista.get(i).getEstrategia() == e) return PALETA[i % PALETA.length];
        }
        return PALETA[0];
    }
}
