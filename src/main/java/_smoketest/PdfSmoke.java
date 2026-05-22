package _smoketest;

import application.dto.ResultadoSimulacionDto;
import domain.value.EstrategiaVacunacion;
import infrastructure.persistence.GeneradorReportePDF;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Smoke test temporal: genera un PDF con datos sintéticos para verificar
 * que GeneradorReportePDF no falla en runtime tras los cambios visuales.
 * No es parte del producto; eliminar tras validar.
 */
public class PdfSmoke {

    public static void main(String[] args) throws Exception {
        List<ResultadoSimulacionDto> datos = new ArrayList<>();
        EstrategiaVacunacion[] todas = EstrategiaVacunacion.values();
        int N = 100;

        for (int s = 0; s < todas.length; s++) {
            List<Map<String, Integer>> hist = new ArrayList<>();
            int pico = 0, turnoPico = 0;
            for (int t = 0; t < 30; t++) {
                int infectados = (int) (40 * Math.exp(-Math.pow((t - 10 - s) / 6.0, 2)));
                int recuperados = Math.min(N - 20, t * 2 + s);
                int vacunados   = 20;
                int susceptibles = Math.max(0, N - infectados - recuperados - vacunados);
                Map<String, Integer> m = new HashMap<>();
                m.put("S", susceptibles); m.put("I", infectados);
                m.put("R", recuperados);  m.put("V", vacunados);
                hist.add(m);
                if (infectados > pico) { pico = infectados; turnoPico = t; }
            }
            datos.add(new ResultadoSimulacionDto(todas[s], hist,
                    pico, turnoPico, 28 - s, 50 + s * 2, 20, N, 1.2 + s * 0.05));
        }

        File out = new File("smoke_reporte.pdf");
        new GeneradorReportePDF().exportar(datos, out.getAbsolutePath());
        System.out.println("OK -> " + out.getAbsolutePath() + " (" + out.length() + " bytes)");
    }
}
