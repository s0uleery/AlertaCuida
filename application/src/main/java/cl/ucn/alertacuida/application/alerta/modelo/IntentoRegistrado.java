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
 */
public record IntentoRegistrado(UUID vinculo, String contacto, Canal canal, Integer ronda, Instant envio,
		Instant entrega, Instant lectura, Instant fallo, String motivoFallo, String respuesta, Instant fechaRespuesta) {
}
