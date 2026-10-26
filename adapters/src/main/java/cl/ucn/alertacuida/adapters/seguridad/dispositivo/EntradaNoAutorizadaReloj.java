package cl.ucn.alertacuida.adapters.seguridad.dispositivo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Respuesta 401 de la API del reloj, en el formato de errores del contrato (RFC 9457 con
 * "codigo"). Sirve tanto si falta el token como si ya no vale: el mismo mensaje en ambos casos.
 */
@Component
public class EntradaNoAutorizadaReloj implements AuthenticationEntryPoint {

	private static final String CUERPO = """
			{"type":"about:blank","title":"Unauthorized","status":401,\
			"detail":"Token del reloj ausente, desconocido o revocado","codigo":"TOKEN_INVALIDO"}""";

	@Override
	public void commence(HttpServletRequest peticion, HttpServletResponse respuesta, AuthenticationException error)
			throws IOException {
		respuesta.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		respuesta.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
		respuesta.getWriter().write(CUERPO);
	}

}
