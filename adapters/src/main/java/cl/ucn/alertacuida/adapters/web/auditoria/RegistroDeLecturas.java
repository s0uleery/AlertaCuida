package cl.ucn.alertacuida.adapters.web.auditoria;

import cl.ucn.alertacuida.adapters.seguridad.TokenJwt;
import cl.ucn.alertacuida.application.auditoria.RegistrarLectura;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Registra las lecturas de los endpoints con {@link LecturaAuditada}. Corre justo antes de escribir la
 * respuesta: una consulta fallida no llega hasta aquí, y si el registro falla, los datos no salen.
 */
@RestControllerAdvice
public class RegistroDeLecturas implements ResponseBodyAdvice<Object> {

	private final RegistrarLectura lecturas;

	/** @param lecturas deja constancia de quién leyó qué */
	public RegistroDeLecturas(RegistrarLectura lecturas) {
		this.lecturas = lecturas;
	}

	/** @return true solo en los métodos marcados con {@link LecturaAuditada} */
	@Override
	public boolean supports(MethodParameter metodo, Class<? extends HttpMessageConverter<?>> conversor) {
		return metodo.hasMethodAnnotation(LecturaAuditada.class);
	}

	/** @return el mismo cuerpo, después de registrar la lectura */
	@Override
	public Object beforeBodyWrite(Object cuerpo, MethodParameter metodo, MediaType tipo,
			Class<? extends HttpMessageConverter<?>> conversor, ServerHttpRequest peticion,
			ServerHttpResponse respuesta) {
		HttpServletRequest servlet = ((ServletServerHttpRequest) peticion).getServletRequest();
		LecturaAuditada lectura = metodo.getMethodAnnotation(LecturaAuditada.class);
		Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		lecturas.registrar(TokenJwt.leer(jwt).id(), lectura.entidad(), identificador(lectura, servlet),
				servlet.getRemoteAddr());
		return cuerpo;
	}

	/** @return el valor de la variable de la ruta, o {@link RegistrarLectura#LISTA} si se leyó una lista */
	@SuppressWarnings("unchecked")
	private static String identificador(LecturaAuditada lectura, HttpServletRequest peticion) {
		if (lectura.idVariable().isEmpty()) {
			return RegistrarLectura.LISTA;
		}
		Map<String, String> variables = (Map<String, String>) peticion
				.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
		return variables.get(lectura.idVariable());
	}

}
