package cl.ucn.alertacuida.adapters.web.sesion;

import cl.ucn.alertacuida.adapters.seguridad.PropiedadesSeguridad;
import cl.ucn.alertacuida.application.sesion.modelo.TokenRenovacion;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * La cookie que lleva el token de renovación. HttpOnly: el código del navegador no puede leerla.
 * SameSite=Strict: otros sitios no pueden provocar su envío. Solo viaja a /api/auth.
 */
@Component
public class CookieRenovacion {

	/** Nombre de la cookie, igual al que lee el controlador. */
	static final String NOMBRE = "alertacuida_renovacion";
	private static final String RUTA = "/api/auth";

	private final boolean segura;
	private final Clock reloj;

	/**
	 * @param propiedades dicen si la cookie exige HTTPS
	 * @param reloj       hora actual, para calcular cuánto le queda
	 */
	public CookieRenovacion(PropiedadesSeguridad propiedades, Clock reloj) {
		this.segura = propiedades.sesion().cookieSegura();
		this.reloj = reloj;
	}

	/**
	 * @param token token de renovación recién emitido
	 * @return la cookie que lo guarda en el navegador hasta el límite de la sesión
	 */
	ResponseCookie con(TokenRenovacion token) {
		return base(token.valor(), Duration.between(reloj.instant(), token.expiraEn()));
	}

	/** @return la cookie que le indica al navegador borrar la anterior */
	ResponseCookie borrada() {
		return base("", Duration.ZERO);
	}

	/** Los atributos deben coincidir en ambos casos para que el navegador reconozca la cookie. */
	private ResponseCookie base(String valor, Duration duracion) {
		return ResponseCookie.from(NOMBRE, valor)
				.httpOnly(true)
				.secure(segura)
				.sameSite("Strict")
				.path(RUTA)
				.maxAge(duracion)
				.build();
	}

}
