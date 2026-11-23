package cl.ucn.alertacuida.application.calendario;

import cl.ucn.alertacuida.application.contacto.modelo.ContactoDelPaciente;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import cl.ucn.alertacuida.domain.calendario.CalendarioHabil;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import cl.ucn.alertacuida.domain.contacto.Horario;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

/**
 * A quién se avisa según el tipo y la gravedad de la alerta y la hora (Anexo B). La regla es fija:
 * <ul>
 * <li>una alerta leve fuera del horario hábil (o en feriado) no avisa: espera al día hábil;</li>
 * <li>en cualquier otro caso se avisa a los contactos disponibles a esa hora, por prioridad;</li>
 * <li>con el botón de ayuda, primero los vecinos (por prioridad) y después el resto (por prioridad).</li>
 * </ul>
 * El horario de cada contacto se respeta siempre, también en horario hábil.
 *
 * <p>El reloj aplica la misma regla, porque es él quien escala; el servidor la usa para revisar
 * las alertas que recibe y para las suyas.
 */
public class ReglaEnrutamiento {

	/** Tipo de contacto al que avisa primero el botón de ayuda (tabla tipo_contacto). */
	static final String TIPO_VECINO = "VECINO";

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
	 * @param tipo      qué se detectó: con BOTON_AYUDA los vecinos van primero
	 * @param contactos la lista del paciente, en cualquier orden
	 * @return los contactos a los que se avisa, en orden: activos, con algún canal usable y con un
	 *         horario que cubre ese momento. Vacía si la alerta espera al día hábil
	 */
	public List<ContactoDelPaciente> contactosAAvisar(TipoEvento tipo, Severidad severidad, Instant momento,
			List<ContactoDelPaciente> contactos) {
		if (esperaDiaHabil(severidad, momento)) {
			return List.of();
		}
		LocalTime hora = momento.atZone(ZonaHoraria.CHILE).toLocalTime();
		Comparator<ContactoDelPaciente> orden = Comparator.comparingInt(ContactoDelPaciente::prioridad);
		if (tipo == TipoEvento.BOTON_AYUDA) {
			orden = Comparator.comparing((ContactoDelPaciente contacto) -> !esVecino(contacto)).thenComparing(orden);
		}
		return contactos.stream()
				.filter(ContactoDelPaciente::disponible)
				.filter(contacto -> cubre(contacto.vinculo().horario(), hora))
				.sorted(orden)
				.toList();
	}

	/** @return si el contacto es vecino del paciente */
	private static boolean esVecino(ContactoDelPaciente contacto) {
		return TIPO_VECINO.equals(contacto.vinculo().tipo());
	}

	/** @return si el contacto recibe avisos a esa hora; sin horario, todo el día */
	private static boolean cubre(Horario horario, LocalTime hora) {
		return horario == null || horario.cubre(hora);
	}

}
