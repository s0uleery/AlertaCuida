package cl.ucn.alertacuida.adapters.web.sesion;

import cl.ucn.alertacuida.application.sesion.SesionIniciada;
import java.time.Instant;

public record RespuestaLogin(String token, Instant expiraEn, UsuarioRespuesta usuario) {

	static RespuestaLogin de(SesionIniciada sesion) {
		return new RespuestaLogin(sesion.token().valor(), sesion.token().expiraEn(),
				UsuarioRespuesta.de(sesion.usuario()));
	}

}
