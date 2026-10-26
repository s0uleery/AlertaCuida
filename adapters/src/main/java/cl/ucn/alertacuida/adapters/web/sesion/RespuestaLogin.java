package cl.ucn.alertacuida.adapters.web.sesion;

import cl.ucn.alertacuida.application.sesion.modelo.SesionIniciada;
import java.time.Instant;

/**
 * Respuesta de {@code POST /api/auth/login}.
 *
 * @param token    va en la cabecera "Authorization: Bearer ..." de cada petición
 * @param expiraEn desde cuándo hay que iniciar sesión de nuevo
 * @param usuario  quién entró y qué puede hacer (el front arma el menú con esto)
 */
public record RespuestaLogin(String token, Instant expiraEn, UsuarioRespuesta usuario) {

	/** @return la sesión del caso de uso en el formato JSON del dashboard */
	static RespuestaLogin de(SesionIniciada sesion) {
		return new RespuestaLogin(sesion.token().valor(), sesion.token().expiraEn(),
				UsuarioRespuesta.de(sesion.usuario()));
	}

}
