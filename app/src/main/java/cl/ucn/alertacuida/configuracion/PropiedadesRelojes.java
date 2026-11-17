package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.domain.reloj.UmbralesSemaforo;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Valores propios del semáforo de los relojes (alertacuida.relojes en application.properties). El resto de sus
 * umbrales son los de las reglas de ausencia, para que el semáforo y las alertas técnicas no se contradigan.
 *
 * @param atraso        tiempo sin sincronizar desde el cual (sin incluirlo) se marca atraso, menor que el silencio
 * @param avisoPlanDias con cuántos días de anticipación se avisa que el plan de datos de la SIM vence
 */
@Validated
@ConfigurationProperties("alertacuida.relojes")
public record PropiedadesRelojes(@NotNull Duration atraso, @Min(0) int avisoPlanDias) {

	/** @return los umbrales del semáforo, con los de las reglas de ausencia */
	public UmbralesSemaforo umbrales(PropiedadesVigilancia vigilancia) {
		return new UmbralesSemaforo(vigilancia.silencio(), vigilancia.bateriaMinima(), vigilancia.horaInicioDia(),
				vigilancia.horaFinDia(), atraso, avisoPlanDias);
	}

}
