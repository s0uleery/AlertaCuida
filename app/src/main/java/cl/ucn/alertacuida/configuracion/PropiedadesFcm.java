package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.application.aviso.modelo.PoliticaReintentos;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Avisos al reloj por Firebase (alertacuida.fcm.* en application.properties).
 *
 * @param canal        FIREBASE envía de verdad; SIMULADO solo registra en el log; SIMULADOR entrega al
 *                     simulador de reloj por HTTP local (desarrollo)
 * @param credenciales ruta del archivo de la cuenta de servicio; obligatoria con FIREBASE
 * @param simuladorUrl dónde escucha el simulador de reloj; obligatoria con SIMULADOR
 * @param intervalo    cada cuánto corre la tarea programada; lo lee directamente TareasProgramadas
 * @param reintentos   espera antes de cada reintento de un aviso sin acuse
 */
@Validated
@ConfigurationProperties("alertacuida.fcm")
public record PropiedadesFcm(@NotNull Canal canal, String credenciales, URI simuladorUrl,
		@NotNull Duration intervalo, @NotEmpty List<Duration> reintentos) {

	/** Por dónde salen los avisos. */
	public enum Canal {
		/** Firebase Cloud Messaging. */
		FIREBASE,
		/** No envía nada: registra el aviso en el log. */
		SIMULADO,
		/** Lo entrega por HTTP al simulador de reloj, que corre en el mismo computador. */
		SIMULADOR
	}

	/** @return las esperas entre reintentos */
	public PoliticaReintentos politica() {
		return new PoliticaReintentos(reintentos);
	}

}
