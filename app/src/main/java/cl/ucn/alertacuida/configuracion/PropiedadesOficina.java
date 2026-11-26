package cl.ucn.alertacuida.configuracion;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Pasos de la oficina PRLAC en el enlace del correo (alertacuida.oficina.* en application.properties).
 *
 * @param recordatorioLlegada tiempo desde "Personal asignado" sin "Ya llegó" tras el que se envía el correo
 *                            recordatorio (N1-08) y la página muestra "Sigue en camino"
 * @param intervalo           cada cuánto corre la tarea del recordatorio; lo lee directamente TareasProgramadas
 */
@Validated
@ConfigurationProperties("alertacuida.oficina")
public record PropiedadesOficina(@NotNull Duration recordatorioLlegada, @NotNull Duration intervalo) {
}
