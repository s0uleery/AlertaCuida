package cl.ucn.alertacuida.application.aviso.modelo;

import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.util.Map;

/**
 * Los textos editables de los correos, con marcadores entre llaves como {folio}. Se leen de correos.yml;
 * qué marcador sirve en cada texto está explicado en ese archivo. Un marcador con mayúscula inicial, como
 * {Reloj}, pone en mayúscula la primera letra de su valor.
 *
 * @param pieEquipo     cierre de los correos al equipo ({pie} en sus cuerpos)
 * @param pieContacto   cierre de los correos al contacto ({pie} en su cuerpo)
 * @param silencioDatos al equipo, cuando un reloj deja de sincronizar
 * @param alertaEquipo  al equipo, por una alerta
 * @param contacto      a un contacto del paciente
 * @param gravedades    cómo se nombra cada gravedad ({gravedad})
 * @param tecnicas      cómo se nombra cada alerta técnica ({tecnica}), menos el silencio de datos
 */
public record TextosCorreo(String pieEquipo, String pieContacto, SilencioDatos silencioDatos,
		AlertaEquipo alertaEquipo, Contacto contacto, Map<Severidad, String> gravedades,
		Map<TipoEvento, String> tecnicas) {

	/**
	 * @param asunto        admite {reloj}, {folio} y {dashboard}
	 * @param cuerpo        admite {reloj}, {folio}, {dashboard} y {pie}
	 * @param relojConAlias valor de {reloj} si el paciente tiene alias; admite {alias}
	 * @param relojSinAlias valor de {reloj} si no lo tiene
	 */
	public record SilencioDatos(String asunto, String cuerpo, String relojConAlias, String relojSinAlias) {
	}

	/**
	 * @param asunto          admite {alerta}, {deQuien}, {folio} y {dashboard}
	 * @param cuerpo          admite {alerta}, {deQuien}, {folio}, {dashboard} y {pie}
	 * @param alertaSalud     valor de {alerta} en una alerta de salud; admite {gravedad} y {folio}
	 * @param alertaTecnica   valor de {alerta} en una técnica; admite {tecnica} y {folio}
	 * @param deQuienConAlias valor de {deQuien} si el paciente tiene alias; admite {alias}. Sin alias, vacío
	 */
	public record AlertaEquipo(String asunto, String cuerpo, String alertaSalud, String alertaTecnica,
			String deQuienConAlias) {
	}

	/**
	 * @param asunto        admite {quien} y {folio}
	 * @param cuerpo        admite {quien}, {folio}, {grave} y {pie}; exige {responder}, el enlace a la página
	 *                      donde elige "Confirmo que voy" o "No puedo asistir"
	 * @param quienSinAlias valor de {quien} si el paciente no tiene alias (con alias, el alias)
	 * @param grave         valor de {grave} en una alerta grave; en las demás queda vacío
	 */
	public record Contacto(String asunto, String cuerpo, String quienSinAlias, String grave) {
	}

}
