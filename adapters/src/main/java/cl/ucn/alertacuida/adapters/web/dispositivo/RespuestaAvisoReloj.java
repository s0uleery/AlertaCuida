package cl.ucn.alertacuida.adapters.web.dispositivo;

import cl.ucn.alertacuida.application.alerta.modelo.EstadoAlerta;
import cl.ucn.alertacuida.application.escalamiento.modelo.MotivoRechazo;
import cl.ucn.alertacuida.application.escalamiento.modelo.RespuestaAviso;
import cl.ucn.alertacuida.application.escalamiento.modelo.ResultadoAviso;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Respuesta de {@code POST /alertas/{eventId}/avisos} (esquema RespuestaAviso de docs/api-reloj.yaml).
 * {@code motivo} solo viene si fue rechazado e {@code idIntento} solo si fue aceptado; el estado de la
 * alerta viene siempre, con todos sus campos.
 *
 * @param motivo       solo si fue rechazado
 * @param idIntento    solo si fue aceptado
 * @param estadoAlerta para que el reloj sepa si debe detenerse
 */
public record RespuestaAvisoReloj(
		ResultadoAviso resultado,
		@JsonInclude(JsonInclude.Include.NON_NULL) MotivoRechazo motivo,
		@JsonInclude(JsonInclude.Include.NON_NULL) UUID idIntento,
		EstadoAlerta estadoAlerta) {

	/** @return la respuesta del caso de uso en el formato del contrato */
	static RespuestaAvisoReloj de(RespuestaAviso respuesta) {
		return new RespuestaAvisoReloj(respuesta.resultado(), respuesta.motivo(), respuesta.idIntento(),
				respuesta.estadoAlerta());
	}

}
