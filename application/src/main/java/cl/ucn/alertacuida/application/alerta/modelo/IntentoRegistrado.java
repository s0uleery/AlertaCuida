package cl.ucn.alertacuida.application.alerta.modelo;

import cl.ucn.alertacuida.domain.contacto.Canal;
import java.time.Instant;
import java.util.UUID;

/**
 * Un aviso enviado o por enviar, con sus estados de entrega.
 *
 * @param vinculo   el contacto en la lista del paciente; null si el aviso fue a una cuenta del equipo
 * @param contacto  nombre de la persona o de la cuenta
 * @param ronda     null en los avisos al equipo
 * @param respuesta botón que pulsó el contacto; null si no respondió
 * @param llamada   cuándo presionó "Llamar al paciente"; null si no lo hizo
 * @param llegada   cuándo avisó que llegó donde el paciente; null si no lo hizo
 * @param resultadoLlamada      si le contestaron después de llamar (CONTESTA o NO_CONTESTA); null si no lo dijo
 * @param fechaResultadoLlamada cuándo lo dijo
 */
public record IntentoRegistrado(UUID vinculo, String contacto, Canal canal, Integer ronda, Instant envio,
		Instant entrega, Instant lectura, Instant fallo, String motivoFallo, String respuesta, Instant fechaRespuesta,
		Instant llamada, Instant llegada, String resultadoLlamada, Instant fechaResultadoLlamada) {
}
