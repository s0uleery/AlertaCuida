package cl.ucn.alertacuida.application.calendario;

import cl.ucn.alertacuida.application.contacto.modelo.ContactoDelPaciente;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.calendario.CalendarioHabil;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import cl.ucn.alertacuida.domain.contacto.Horario;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

/**
 * A quién se avisa según la gravedad de la alerta y la hora (Anexo B). La regla es fija:
 * <ul>
 * <li>una alerta leve fuera del horario hábil (o en feriado) no avisa: espera al día hábil;</li>
 * <li>en cualquier otro caso se avisa a los contactos de la lista de esa gravedad disponibles a esa hora, en
 * el orden de esa lista (decisión del 26-11-2026). El botón de ayuda es leve: usa la lista de leve.</li>
 * </ul>
 * El horario de cada contacto se respeta siempre, también en horario hábil. Si la alerta se agrava, el reloj
 * sigue con la lista de grave desde el primero.
 *
 * <p>El reloj aplica la misma regla, porque es él quien escala; el servidor la usa para revisar
 * las alertas que recibe y para las suyas.
 */
public class ReglaEnrutamiento {

	private final CalendarioHabil calendario;

	/** Recibe el calendario de calendario.yml; lo arma ConfiguracionCalendario. */
	public ReglaEnrutamiento(CalendarioHabil calendario) {
		this.calendario = calendario;
	}

	/** @return si una alerta de esa gravedad, detectada en ese momento, espera al día hábil sin avisar */
	public boolean esperaDiaHabil(Severidad severidad, Instant momento) {
		return severidad == Severidad.LEVE && !calendario.esHabil(momento);
	}

	/**
	 * @param severidad la gravedad de la alerta: decide qué lista se recorre
	 * @param contactos la lista del paciente, en cualquier orden
	 * @return los contactos a los que se avisa, en el orden de la lista de esa gravedad: activos, con algún
	 *         canal usable y con un horario que cubre ese momento. Vacía si la alerta espera al día hábil
	 */
	public List<ContactoDelPaciente> contactosAAvisar(Severidad severidad, Instant momento,
			List<ContactoDelPaciente> contactos) {
		if (esperaDiaHabil(severidad, momento)) {
			return List.of();
		}
		LocalTime hora = momento.atZone(ZonaHoraria.CHILE).toLocalTime();
		return contactos.stream()
				.filter(ContactoDelPaciente::disponible)
				.filter(contacto -> contacto.orden(severidad) != null)
				.filter(contacto -> cubre(contacto.vinculo().horario(), hora))
				.sorted(Comparator.comparing(contacto -> contacto.orden(severidad)))
				.toList();
	}

	/** @return si el contacto recibe avisos a esa hora; sin horario, todo el día */
	private static boolean cubre(Horario horario, LocalTime hora) {
		return horario == null || horario.cubre(hora);
	}

}
