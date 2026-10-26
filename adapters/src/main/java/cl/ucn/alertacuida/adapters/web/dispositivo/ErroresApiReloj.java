package cl.ucn.alertacuida.adapters.web.dispositivo;

import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.telemetria.error.LoteGrande;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Errores de la API del reloj: RFC 9457 como el dashboard, más un "codigo" estable para que el
 * reloj decida qué hacer sin leer el texto (esquema Problema de docs/api-reloj.yaml).
 *
 * <p>Solo atiende a los controladores de este paquete y va antes que {@code ManejadorErrores}.
 * Hereda de {@link ResponseEntityExceptionHandler} para dar el mismo formato a los errores de
 * Spring: JSON ilegible, un tipo que no existe o un campo obligatorio vacío son 400 MENSAJE_INVALIDO.
 */
@RestControllerAdvice(basePackageClasses = SincronizacionesController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ErroresApiReloj extends ResponseEntityExceptionHandler {

	/** @return 400 MENSAJE_INVALIDO: un valor imposible (batería 150, un tipo que el reloj no informa) */
	@ExceptionHandler(SolicitudRechazada.class)
	ProblemDetail solicitudRechazada(SolicitudRechazada error) {
		return problema(HttpStatus.BAD_REQUEST, error.getMessage(), "MENSAJE_INVALIDO");
	}

	/** @return 404 ALERTA_DESCONOCIDA: el reloj debe reenviar primero la alerta */
	@ExceptionHandler(NoEncontrado.class)
	ProblemDetail noEncontrado(NoEncontrado error) {
		return problema(HttpStatus.NOT_FOUND, error.getMessage(), "ALERTA_DESCONOCIDA");
	}

	/** @return 409 NO_APLICA: el eventId es de otro reloj, o una alerta llega como cancelación */
	@ExceptionHandler(Conflicto.class)
	ProblemDetail conflicto(Conflicto error) {
		return problema(HttpStatus.CONFLICT, error.getMessage(), "NO_APLICA");
	}

	/** @return 413 LOTE_GRANDE: el reloj debe partir el historial en lotes más chicos */
	@ExceptionHandler(LoteGrande.class)
	ProblemDetail loteGrande(LoteGrande error) {
		return problema(HttpStatus.CONTENT_TOO_LARGE, error.getMessage(), "LOTE_GRANDE");
	}

	/** Agrega el código a los errores que arma Spring (cuerpo ilegible, validación, método no permitido…). */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception error, Object cuerpo, HttpHeaders cabeceras,
			HttpStatusCode estado, WebRequest peticion) {
		ResponseEntity<Object> respuesta = super.handleExceptionInternal(error, cuerpo, cabeceras, estado, peticion);
		if (estado.is4xxClientError() && respuesta != null && respuesta.getBody() instanceof ProblemDetail problema) {
			problema.setProperty("codigo", "MENSAJE_INVALIDO");
		}
		return respuesta;
	}

	/** @return el error con su mensaje y su código */
	private static ProblemDetail problema(HttpStatus estado, String mensaje, String codigo) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, mensaje);
		problema.setProperty("codigo", codigo);
		return problema;
	}

}
