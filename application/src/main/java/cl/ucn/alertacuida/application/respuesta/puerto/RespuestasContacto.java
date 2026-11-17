package cl.ucn.alertacuida.application.respuesta.puerto;

import cl.ucn.alertacuida.application.respuesta.modelo.AvisoRespondible;
import cl.ucn.alertacuida.application.respuesta.modelo.RespuestaContacto;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Avisos por correo a contactos del paciente y sus respuestas (columnas respuesta de registro_intento). */
public interface RespuestasContacto {

	/**
	 * @return el aviso, solo si es un correo a un contacto de la lista del paciente; vacío si no existe o si
	 *         fue a una cuenta del equipo, que no confirma alertas
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

}
