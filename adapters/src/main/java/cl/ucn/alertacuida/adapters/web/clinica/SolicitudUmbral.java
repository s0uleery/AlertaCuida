package cl.ucn.alertacuida.adapters.web.clinica;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Cuerpo de {@code PUT /api/pacientes/{id}/perfil-clinico/umbrales/{variable}}. El orden de los
 * límites se revisa en el caso de uso.
 *
 * @param graveBajo bajo este valor la lectura es grave
 * @param normalMin desde este valor la lectura es normal
 * @param normalMax hasta este valor la lectura es normal (no va en SpO2)
 * @param graveAlto sobre este valor la lectura es grave (no va en SpO2)
 */
public record SolicitudUmbral(@NotNull BigDecimal graveBajo, @NotNull BigDecimal normalMin, BigDecimal normalMax,
		BigDecimal graveAlto) {
}
