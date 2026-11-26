package cl.ucn.alertacuida.application.respuesta.puerto;

import cl.ucn.alertacuida.application.respuesta.modelo.AvisoRespondible;
import cl.ucn.alertacuida.application.respuesta.modelo.RespuestaContacto;
import cl.ucn.alertacuida.application.respuesta.modelo.ResultadoLlamadaContacto;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Avisos que piden respuesta (a contactos del paciente y a coordinadores) y las respuestas de los contactos
 * (columnas respuesta, llamada y llegada de registro_intento).
 */
public interface RespuestasContacto {

	/**
	 * @return el aviso, si pide respuesta: a un contacto de la lista del paciente o, en las alertas de salud, a un
	 *         coordinador (aviso a la oficina, agravamiento o recordatorio). Vacío si no existe o si es otro aviso
	 *         al equipo, que no pide respuesta
	 */
	Optional<AvisoRespondible> buscar(UUID idIntento);

	/**
	 * Guarda la respuesta solo si el aviso aún no tiene una. Debe llamarse dentro de una transacción.
	 *
	 * @return si la guardó
	 */
	boolean registrar(UUID idIntento, RespuestaContacto respuesta, Instant fecha);

	/** Deja pendiente el aviso ALERTA_CONFIRMADA al reloj; si ya existe, no hace nada. */
	void dejarAvisoConfirmadaPendiente(UUID idAlerta);

	/**
	 * Guarda que el contacto presionó "Llamar al paciente", solo la primera vez.
	 *
	 * @return si la guardó; false si ya estaba
	 */
	boolean registrarLlamada(UUID idIntento, Instant fecha);

	/**
	 * Guarda lo que dijo el contacto del paciente después de llamar, solo la primera vez (26-11-2026).
	 *
	 * @return si la guardó; false si ya estaba o el aviso no es a un contacto del paciente
	 */
	boolean registrarResultadoLlamada(UUID idIntento, ResultadoLlamadaContacto resultado, Instant fecha);

	/**
	 * Guarda que el contacto llegó donde el paciente, solo si respondió "Voy en camino" y aún no lo había
	 * avisado. Debe llamarse dentro de una transacción.
	 *
	 * @return si la guardó
	 */
	boolean registrarLlegada(UUID idIntento, Instant fecha);

	/**
	 * Resuelve la alerta por el sistema (sin responsable nuevo, clasificación ni observaciones), solo si sigue
	 * abierta. El equipo completa el cierre después.
	 *
	 * @return si la resolvió; false si ya estaba cerrada
	 */
	boolean resolverPorLlegada(UUID idAlerta, Instant fecha);

	/** Deja pendiente el aviso ALERTA_RESUELTA al reloj; si ya existe, no hace nada. */
	void dejarAvisoResueltaPendiente(UUID idAlerta);

}
