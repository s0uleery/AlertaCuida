package cl.ucn.alertacuida.application.aviso.modelo;

import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.util.UUID;

/**
 * Un correo de la bandeja de salida al que le toca salir, con lo necesario para redactarlo.
 *
 * @param idIntento    su fila en registro_intento
 * @param destinatario correo de la cuenta o del contacto; null si la persona ya no tiene correo
 * @param alEquipo     si va a una cuenta del equipo PRLAC (contacto interno) o a un contacto del paciente
 * @param numero       folio de la alerta
 * @param alias        nombre corto del paciente para los mensajes; puede faltar
 * @param tipo         qué se detectó
 * @param severidad    gravedad actual (grave si una caída se agravó)
 * @param reintentos   cuántos reintentos ya se hicieron
 * @param vigilante    si es el aviso del vigilante del escalamiento (la alerta no avanza); solo al equipo
 */
public record CorreoPendiente(UUID idIntento, String destinatario, boolean alEquipo, long numero, String alias,
		TipoEvento tipo, Severidad severidad, int reintentos, boolean vigilante) {

	/** Un correo que no es del vigilante: un aviso al equipo o a un contacto. */
	public CorreoPendiente(UUID idIntento, String destinatario, boolean alEquipo, long numero, String alias,
			TipoEvento tipo, Severidad severidad, int reintentos) {
		this(idIntento, destinatario, alEquipo, numero, alias, tipo, severidad, reintentos, false);
	}

}
