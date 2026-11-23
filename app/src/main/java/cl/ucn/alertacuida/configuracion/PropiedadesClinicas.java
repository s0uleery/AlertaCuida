package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.domain.alerta.Severidad;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Valores base del sistema (alertacuida.clinica.* en application.properties), que rigen cuando el
 * paciente no tiene ajustes propios. Son los del Anexo B; los actuales son provisionales hasta que
 * los valide el equipo clínico.
 *
 * @param umbralFc      umbral base de la frecuencia cardíaca, en latidos por minuto
 * @param umbralSpo2    umbral base de la saturación de oxígeno, en %
 * @param minutosEspera antes de pasar al siguiente contacto
 * @param versionMatriz versión de la matriz FC × SpO2 que aplica el reloj (PROVISIONAL hasta el Anexo B)
 * @param minutosTendencia minutos seguidos fuera del rango normal, sin llegar al límite grave, para alertar.
 *                         Al menos 30, porque las lecturas llegan cada 30 minutos
 * @param severidadTendencia gravedad con que el reloj envía la alerta de tendencia
 */
@Validated
@ConfigurationProperties("alertacuida.clinica")
public record PropiedadesClinicas(@NotNull @Valid Fc umbralFc, @NotNull @Valid Spo2 umbralSpo2,
		@Min(1) @Max(60) int minutosEspera, @NotBlank String versionMatriz, @Min(30) int minutosTendencia,
		@NotNull Severidad severidadTendencia) {

	/** Límites de la FC: grave bajo &lt; mínimo normal &lt; máximo normal &lt; grave alto. */
	public record Fc(@NotNull BigDecimal graveBajo, @NotNull BigDecimal normalMin, @NotNull BigDecimal normalMax,
			@NotNull BigDecimal graveAlto) {
	}

	/** Límites de la SpO2, que no tiene límite alto: grave bajo &lt; mínimo normal. */
	public record Spo2(@NotNull BigDecimal graveBajo, @NotNull BigDecimal normalMin) {
	}

}
