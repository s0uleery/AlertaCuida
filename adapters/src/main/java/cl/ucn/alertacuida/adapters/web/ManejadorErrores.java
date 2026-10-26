package cl.ucn.alertacuida.adapters.web;

import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.personal.error.CuentaDuplicada;
import cl.ucn.alertacuida.application.personal.error.CuentaNoPendiente;
import cl.ucn.alertacuida.application.personal.error.EnlaceInvalido;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones de los casos de uso a respuestas HTTP con el formato estándar
 * ProblemDetail (RFC 9457): el mensaje va en "detail" y el front lo muestra tal cual.
 */
@RestControllerAdvice
public class ManejadorErrores {

	/** @return 400: los datos no cumplen una regla (RUN inválido, contraseña débil, …) */
	@ExceptionHandler(SolicitudRechazada.class)
	ProblemDetail solicitudRechazada(SolicitudRechazada error) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
	}

	/** @return 404: lo pedido no existe */
	@ExceptionHandler(NoEncontrado.class)
	ProblemDetail noEncontrado(NoEncontrado error) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.getMessage());
	}

	/** @return 409: choca con lo guardado (RUN repetido, reloj ya vinculado, …) */
	@ExceptionHandler(Conflicto.class)
	ProblemDetail conflicto(Conflicto error) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.getMessage());
	}

	/** @return 400: el enlace de activación no sirve (mismo mensaje para cualquier causa) */
	@ExceptionHandler(EnlaceInvalido.class)
	ProblemDetail enlaceInvalido(EnlaceInvalido error) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
	}

	/** @return 409: ya hay una cuenta con ese RUN o correo */
	@ExceptionHandler(CuentaDuplicada.class)
	ProblemDetail cuentaDuplicada(CuentaDuplicada error) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.getMessage());
	}

	/** @return 404: no hay una cuenta pendiente con ese identificador */
	@ExceptionHandler(CuentaNoPendiente.class)
	ProblemDetail cuentaNoPendiente(CuentaNoPendiente error) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.getMessage());
	}

}
