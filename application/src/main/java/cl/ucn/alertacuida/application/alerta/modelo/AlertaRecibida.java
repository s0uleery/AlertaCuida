package cl.ucn.alertacuida.application.alerta.modelo;

import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Una alerta, o una alerta que el paciente canceló, tal como la envió el reloj.
 *
 * @param eventId              lo genera el reloj y la identifica de aquí en adelante
 * @param versionConfiguracion la configuración con que el reloj la evaluó
 * @param envio                fin de la ventana de 30 s; null si se canceló
 * @param esperaDiaHabil       leve fuera del horario hábil: el reloj no avisará a nadie
 * @param cancelacion          cuándo la canceló el paciente; null si no la canceló
 * @param evidencia            lo que llevó a alertar, como documento JSON; puede ser null
 * @param lecturas             las lecturas de esa evidencia, para compararlas con los umbrales; vacía si no trae
 */
public record AlertaRecibida(UUID eventId, TipoEvento tipo, Severidad severidad, String versionConfiguracion,
		Instant deteccion, Instant envio, boolean esperaDiaHabil, Instant cancelacion, String evidencia,
		List<LecturaEvidencia> lecturas) {

	public AlertaRecibida {
		lecturas = lecturas == null ? List.of() : List.copyOf(lecturas);
	}

	/** Una alerta sin lecturas leídas de su evidencia. */
	public AlertaRecibida(UUID eventId, TipoEvento tipo, Severidad severidad, String versionConfiguracion,
			Instant deteccion, Instant envio, boolean esperaDiaHabil, Instant cancelacion, String evidencia) {
		this(eventId, tipo, severidad, versionConfiguracion, deteccion, envio, esperaDiaHabil, cancelacion, evidencia,
				List.of());
	}

	/** @return la misma alerta con otra gravedad y otra marca de espera, sin perder sus lecturas */
	public AlertaRecibida con(Severidad otraSeveridad, boolean otraEspera) {
		return new AlertaRecibida(eventId, tipo, otraSeveridad, versionConfiguracion, deteccion, envio, otraEspera,
				cancelacion, evidencia, lecturas);
	}

}
