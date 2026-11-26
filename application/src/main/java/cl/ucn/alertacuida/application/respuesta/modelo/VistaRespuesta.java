package cl.ucn.alertacuida.application.respuesta.modelo;

import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.time.Instant;

/**
 * Lo que ve el contacto en la página de su enlace. Sin datos clínicos: solo cómo se llama la alerta, qué
 * respondió (decisión del 12-11-2026) y, mientras siga abierta, el número para llamar al paciente (24-11-2026).
 *
 * @param numero         folio de la alerta
 * @param alias          nombre corto del paciente; null si no tiene (la página usa el folio)
 * @param tipo           qué se detectó
 * @param severidad      gravedad actual
 * @param fechaHora      cuándo se detectó
 * @param respuesta      lo que respondió; null si aún no responde
 * @param fechaRespuesta cuándo respondió; null si aún no responde
 * @param cerrada        si la alerta ya fue resuelta o cancelada: ya no hace falta responder
 * @param telefonoPaciente número de la SIM del reloj, para "Llamar al paciente"; null si la alerta está cerrada o
 *                       el reloj no tiene número registrado
 * @param llegada        cuándo avisó que llegó donde el paciente; null si no lo ha hecho
 * @param resultadoLlamada lo que dijo el contacto después de llamar (26-11-2026); null si aún no lo dice
 * @param oficina        los pasos de la oficina, si el enlace es de un coordinador; null si es de un contacto del
 *                       paciente
 */
public record VistaRespuesta(long numero, String alias, TipoEvento tipo, Severidad severidad, Instant fechaHora,
		RespuestaContacto respuesta, Instant fechaRespuesta, boolean cerrada, String telefonoPaciente,
		Instant llegada, ResultadoLlamadaContacto resultadoLlamada, VistaOficina oficina) {
}
