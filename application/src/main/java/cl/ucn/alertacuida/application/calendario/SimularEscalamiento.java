package cl.ucn.alertacuida.application.calendario;

import cl.ucn.alertacuida.domain.contacto.Canal;
import cl.ucn.alertacuida.application.contacto.modelo.ContactoDelPaciente;
import cl.ucn.alertacuida.application.contacto.puerto.ListaDelPaciente;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.calendario.CalendarioHabil;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Simulador de canales del Calendario (decisión del 25-11-2026): para un día, una hora y una gravedad dice qué
 * pasaría con una alerta de salud según las reglas que el sistema aplica hoy, sin crear nada:
 * <ul>
 * <li>si el momento es hábil o no (y si es feriado);</li>
 * <li>si la alerta espera al día hábil sin avisar (leve fuera de horario);</li>
 * <li>si se avisa a la oficina PRLAC (coordinadores, solo en horario hábil);</li>
 * <li>y, si se elige un usuario, a qué contactos suyos avisaría el reloj, en orden, y por qué canal.</li>
 * </ul>
 * Usa {@link ReglaEnrutamiento}, la misma regla que aplica el reloj: cuando cambie el escalamiento, el simulador
 * cambia con ella.
 */
public class SimularEscalamiento {

	private final ReglaEnrutamiento regla;
	private final CalendarioHabil calendario;
	private final ListaDelPaciente contactos;

	/** Recibe lo que usa; lo arma ConfiguracionCalendario. */
	public SimularEscalamiento(ReglaEnrutamiento regla, CalendarioHabil calendario, ListaDelPaciente contactos) {
		this.regla = regla;
		this.calendario = calendario;
		this.contactos = contactos;
	}

	/**
	 * @param fecha      el día de la alerta simulada
	 * @param hora       la hora, en Chile
	 * @param gravedad   la gravedad de la alerta
	 * @param idPaciente el usuario cuyos contactos se simulan; null para ver solo las reglas generales
	 * @return lo que pasaría
	 */
	public Simulacion simular(LocalDate fecha, LocalTime hora, Severidad gravedad, UUID idPaciente) {
		Instant momento = fecha.atTime(hora).atZone(ZonaHoraria.CHILE).toInstant();
		boolean habil = calendario.esHabil(momento);
		boolean espera = regla.esperaDiaHabil(gravedad, momento);
		List<PasoSimulado> pasos = new ArrayList<>();
		if (idPaciente != null) {
			List<ContactoDelPaciente> aAvisar = regla.contactosAAvisar(gravedad, momento,
					contactos.delPaciente(idPaciente));
			for (int i = 0; i < aAvisar.size(); i++) {
				ContactoDelPaciente contacto = aAvisar.get(i);
				pasos.add(new PasoSimulado(i + 1, contacto.persona().nombre(), contacto.vinculo().tipo(),
						contacto.vinculo().parentesco(), contacto.vinculo().cuidadorPrincipal(), contacto.canales()));
			}
		}
		return new Simulacion(habil, calendario.esFeriado(fecha), espera, habil && !espera, pasos);
	}

	/**
	 * Lo que pasaría con la alerta.
	 *
	 * @param habil         si el momento está dentro del horario hábil y no es feriado
	 * @param feriado       si ese día es feriado
	 * @param esperaDiaHabil si la alerta no avisa a nadie y espera al día hábil (leve fuera de horario)
	 * @param avisoOficina  si se avisa a los coordinadores de la oficina PRLAC (en horario hábil)
	 * @param contactos     a quiénes avisaría el reloj, en orden; vacía sin usuario o si espera al día hábil
	 */
	public record Simulacion(boolean habil, boolean feriado, boolean esperaDiaHabil, boolean avisoOficina,
			List<PasoSimulado> contactos) {
	}

	/**
	 * Un contacto en el orden en que el reloj le avisaría.
	 *
	 * @param orden             1, 2, 3…
	 * @param nombre            el contacto
	 * @param tipo              TERCERO_SIGNIFICATIVO, CARABINEROS, SEGURIDAD_CIUDADANA…
	 * @param parentesco        si es un tercero significativo; puede faltar
	 * @param cuidadorPrincipal si es la persona cuidadora principal
	 * @param canales           por dónde se le puede avisar (WhatsApp con opt-in vigente, correo)
	 */
	public record PasoSimulado(int orden, String nombre, String tipo, String parentesco, boolean cuidadorPrincipal,
			List<Canal> canales) {
	}

}
