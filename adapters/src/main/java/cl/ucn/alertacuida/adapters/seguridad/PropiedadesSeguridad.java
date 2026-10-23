package cl.ucn.alertacuida.adapters.seguridad;

import jakarta.validation.Valid;
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
 * @param duracionToken vida del token de acceso
 * @param bloqueo       cuándo se bloquea una cuenta por intentos fallidos
 * @param activacion    enlaces para que el personal nuevo elija su contraseña
 */
@Validated
@ConfigurationProperties("alertacuida.seguridad")
public record PropiedadesSeguridad(
		@NotNull @Size(min = 16) String claveRun,
		@NotNull @Size(min = 32) String claveJwt,
		@NotNull Duration duracionToken,
		@NotNull @Valid Bloqueo bloqueo,
		@NotNull @Valid Activacion activacion) {

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
