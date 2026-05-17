package application.command;

import application.service.VacunacionService;
import domain.model.Persona;
import domain.model.RedSocial;
import domain.value.EstrategiaVacunacion;
import java.util.List;

/**
 * Aplica la vacunación del 20% antes de iniciar la simulación.
 */
public class AplicarVacunacionCommand {

    private final VacunacionService vacunacionService;

    public AplicarVacunacionCommand(VacunacionService vacunacionService) {
        this.vacunacionService = vacunacionService;
    }

    /**
     * Vacuna el 20% de la población susceptible según la estrategia indicada.
     *
     * @return lista de personas vacunadas
     */
    public List<Persona> ejecutar(RedSocial red, EstrategiaVacunacion estrategia) {
        List<Persona> vacunados = vacunacionService.vacunar(red, estrategia);
        System.out.printf("  Vacunados (%s): %d personas%n", estrategia, vacunados.size());
        return vacunados;
    }
}
