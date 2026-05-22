package infrastructure.persistence;

import domain.model.Persona;
import domain.model.RedSocial;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Carga una RedSocial desde archivos CSV.
 *
 * Formato personas: id,edad,estrato,ocupacion
 * Formato contactos: origen,destino,probContagio
 *
 * Por compatibilidad acepta también el formato antiguo
 * (id,nombre,edad,estrato,ocupacion): si detecta 5 columnas, ignora la segunda.
 */
public class CargadorRedCSV {

    /**
     * Carga la red desde los archivos CSV indicados.
     *
     * @throws IOException si los archivos no existen o el formato es incorrecto
     */
    public RedSocial cargar(String archivoPersonas, String archivoContactos) throws IOException {
        RedSocial red = new RedSocial();
        Map<String, Persona> indice = new HashMap<>();

        // Leer personas
        try (BufferedReader br = new BufferedReader(new FileReader(archivoPersonas))) {
            String linea = br.readLine(); // saltar cabecera
            while ((linea = br.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty()) continue;
                String[] cols = linea.split(",", -1);
                if (cols.length < 4) continue;

                String id;
                int edad;
                int estrato;
                String ocup;

                if (cols.length >= 5) {
                    // Formato antiguo: id,nombre,edad,estrato,ocupacion (nombre ignorado)
                    id      = cols[0].trim();
                    edad    = Integer.parseInt(cols[2].trim());
                    estrato = Integer.parseInt(cols[3].trim());
                    ocup    = cols[4].trim();
                } else {
                    // Formato nuevo: id,edad,estrato,ocupacion
                    id      = cols[0].trim();
                    edad    = Integer.parseInt(cols[1].trim());
                    estrato = Integer.parseInt(cols[2].trim());
                    ocup    = cols[3].trim();
                }

                Persona p = new Persona(id, edad, estrato, ocup);
                red.agregarPersona(p);
                indice.put(id, p);
            }
        }

        // Leer contactos
        try (BufferedReader br = new BufferedReader(new FileReader(archivoContactos))) {
            String linea = br.readLine(); // saltar cabecera
            while ((linea = br.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty()) continue;
                String[] cols = linea.split(",", -1);
                if (cols.length < 3) continue;

                String idOrigen  = cols[0].trim();
                String idDestino = cols[1].trim();
                double prob      = Double.parseDouble(cols[2].trim());

                Persona origen  = indice.get(idOrigen);
                Persona destino = indice.get(idDestino);
                if (origen != null && destino != null) {
                    red.agregarContacto(origen, destino, prob);
                }
            }
        }

        return red;
    }
}
