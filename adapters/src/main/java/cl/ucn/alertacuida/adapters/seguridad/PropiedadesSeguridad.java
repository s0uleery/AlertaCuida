package cl.ucn.alertacuida.adapters.seguridad;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Valores de alertacuida.seguridad.* (application.properties). Las claves llegan por variables de entorno.
 *
 * @param claveRun      clave del HMAC con que se calcula rut_hash. Si cambia, nadie encuentra su RUN
 * @param claveJwt      firma de los tokens (HS256): al menos 32 caracteres
 * @param claveCifrado  llave AES-256 de los datos personales: 32 bytes en base64. Si se pierde, no se
 *                      pueden leer los datos cifrados
 * @param claveRespuesta firma (HMAC) de los enlaces con que un contacto responde a su correo: al menos 32
 *                      caracteres. Si cambia, los enlaces ya enviados dejan de servir
 * @param duracionToken vida del token de acceso
 * @param sesion        cuánto dura una sesión y cómo viaja su token de renovación
 * @param bloqueo       cuándo se bloquea una cuenta por intentos fallidos
 * @param activacion    enlaces para que el personal nuevo elija su contraseña
 */
@Validated
@ConfigurationProperties("alertacuida.seguridad")
public record PropiedadesSeguridad(
		@NotNull @Size(min = 16) String claveRun,
		@NotNull @Size(min = 32) String claveJwt,
		@NotBlank String claveCifrado,
		@NotNull @Size(min = 32) String claveRespuesta,
		@NotNull Duration duracionToken,
		@NotNull @Valid Sesion sesion,
		@NotNull @Valid Bloqueo bloqueo,
		@NotNull @Valid Activacion activacion) {

	/**
	 * El dashboard renueva antes de que se cumpla la inactividad. Si el token de acceso durara más,
	 * una sesión desatendida seguiría sirviendo después de cerrarse por inactividad.
	 *
	 * @return true si el token de acceso no dura más que el plazo de inactividad
	 */
	@AssertTrue(message = "duracion-token no puede superar sesion.inactividad")
	public boolean isTokenNoSuperaInactividad() {
		return duracionToken == null || sesion == null || sesion.inactividad() == null
				|| duracionToken.compareTo(sesion.inactividad()) <= 0;
	}

	/**
	 * Duración de las sesiones del dashboard.
	 *
	 * @param duracionMaxima tiempo desde el login hasta volver a pedir la contraseña
	 * @param inactividad    tiempo sin renovar tras el cual la sesión termina
	 * @param graciaRotacion tiempo en que se acepta un token recién reemplazado (pestañas simultáneas)
	 * @param cookieSegura   true para enviar la cookie solo por HTTPS; false solo en desarrollo local
	 */
	public record Sesion(@NotNull Duration duracionMaxima, @NotNull Duration inactividad,
			@NotNull Duration graciaRotacion, boolean cookieSegura) {
	}

	/** N intentos fallidos en M minutos sin un inicio de sesión correcto bloquean la cuenta. */
	public record Bloqueo(@Min(1) int intentos, @NotNull Duration ventana) {
	}

	/**
	 * Enlaces de activación de cuentas.
	 *
	 * @param url      página del dashboard que recibe el enlace; el token se agrega después de "#"
	 * @param vigencia cuánto dura un enlace sin usar
	 */
	public record Activacion(@NotBlank String url, @NotNull Duration vigencia) {
	}

}
