package cl.ucn.alertacuida.application.respuesta.modelo;

import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.time.Instant;

/**
 * Lo que ve el contacto en la página de su enlace. Sin datos clínicos ni de contacto: solo cómo se llama la
 * alerta y qué respondió (decisión del 12-11-2026).
 *
 * @param numero         folio de la alerta
 * @param alias          nombre corto del paciente; null si no tiene (la página usa el folio)
 * @param tipo           qué se detectó
 * @param severidad      gravedad actual
 * @param fechaHora      cuándo se detectó
 * @param respuesta      lo que respondió; null si aún no responde
 * @param fechaRespuesta cuándo respondió; null si aún no responde
 * @param cerrada        si la alerta ya fue resuelta o cancelada: ya no hace falta responder
 */
public record VistaRespuesta(long numero, String alias, TipoEvento tipo, Severidad severidad, Instant fechaHora,
		RespuestaContacto respuesta, Instant fechaRespuesta, boolean cerrada) {
}
