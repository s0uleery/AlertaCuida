package cl.ucn.alertacuida.application.respuesta.modelo;

import cl.ucn.alertacuida.domain.alerta.EstadoContacto;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.time.Instant;
import java.util.UUID;

/**
 * Un aviso por correo a un contacto del paciente, con lo que hace falta para mostrar su alerta y registrar
 * la respuesta.
 *
 * @param idIntento      su fila en registro_intento
 * @param idAlerta       la alerta avisada
 * @param numero         folio de la alerta
 * @param alias          nombre corto del paciente; puede faltar
 * @param tipo           qué se detectó
 * @param severidad      gravedad actual (grave si una caída se agravó)
 * @param fechaHora      cuándo se detectó (o, si el reloj no lo informó, cuándo se recibió)
 * @param estado         estado de contacto de la alerta ahora
 * @param respuesta      lo que ya respondió este contacto a este aviso; null si aún no responde
 * @param fechaRespuesta cuándo respondió; null si aún no responde
 */
public record AvisoRespondible(UUID idIntento, UUID idAlerta, long numero, String alias, TipoEvento tipo,
		Severidad severidad, Instant fechaHora, EstadoContacto estado, RespuestaContacto respuesta,
		Instant fechaRespuesta) {
}
