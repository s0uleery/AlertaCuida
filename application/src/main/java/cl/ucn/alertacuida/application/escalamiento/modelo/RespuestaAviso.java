package cl.ucn.alertacuida.application.escalamiento.modelo;

import cl.ucn.alertacuida.application.alerta.modelo.EstadoAlerta;
import java.util.UUID;

/**
 * Respuesta a una solicitud de aviso (esquema RespuestaAviso de docs/api-reloj.yaml).
 *
 * @param motivo       solo si fue rechazado
 * @param idIntento    solo si fue aceptado: la fila de registro_intento
 * @param estadoAlerta siempre, para que el reloj sepa si debe detenerse
 */
public record RespuestaAviso(ResultadoAviso resultado, MotivoRechazo motivo, UUID idIntento,
		EstadoAlerta estadoAlerta) {

	/** @return un aviso aceptado, con su intento y el estado de la alerta */
	public static RespuestaAviso aceptado(UUID idIntento, EstadoAlerta estado) {
		return new RespuestaAviso(ResultadoAviso.ACEPTADO, null, idIntento, estado);
	}

	/** @return un aviso rechazado, con su motivo y el estado de la alerta */
	public static RespuestaAviso rechazado(MotivoRechazo motivo, EstadoAlerta estado) {
		return new RespuestaAviso(ResultadoAviso.RECHAZADO, motivo, null, estado);
	}

}
