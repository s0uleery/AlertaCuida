package cl.ucn.alertacuida.adapters.web.sesion;

import cl.ucn.alertacuida.application.sesion.modelo.SesionIniciada;
import java.time.Duration;
import java.time.Instant;

/**
 * Respuesta de {@code POST /api/auth/login} y {@code POST /api/auth/renovar}. El token de renovación
 * no viene aquí: viaja en una cookie que el código del navegador no puede leer.
 *
 * @param token               va en la cabecera "Authorization: Bearer ..." de cada petición
 * @param expiraEn            desde cuándo hay que renovar el token
 * @param inactividadSegundos segundos sin usar el dashboard tras los cuales se cierra la sesión
 * @param usuario             quién entró y qué puede hacer (el front arma el menú con esto)
 */
public record RespuestaLogin(String token, Instant expiraEn, long inactividadSegundos, UsuarioRespuesta usuario) {

	/**
	 * @param sesion      resultado del caso de uso
	 * @param inactividad plazo de inactividad configurado
	 * @return la sesión en el formato JSON del dashboard
	 */
	static RespuestaLogin de(SesionIniciada sesion, Duration inactividad) {
		return new RespuestaLogin(sesion.token().valor(), sesion.token().expiraEn(), inactividad.toSeconds(),
				UsuarioRespuesta.de(sesion.usuario()));
	}

}
