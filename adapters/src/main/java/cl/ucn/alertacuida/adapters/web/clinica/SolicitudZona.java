package cl.ucn.alertacuida.adapters.web.clinica;

import cl.ucn.alertacuida.domain.alerta.Severidad;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Cuerpo de {@code PUT /api/pacientes/{id}/perfil-clinico/zona-segura}. Los rangos se revisan en el
 * caso de uso.
 *
 * @param latitud     del centro, en grados (-90 a 90)
 * @param longitud    del centro, en grados (-180 a 180)
 * @param radioMetros distancia desde el centro que se considera dentro de la zona
 * @param severidad   gravedad de la alerta al salir; vacía = MEDIA
 */
public record SolicitudZona(@NotNull BigDecimal latitud, @NotNull BigDecimal longitud, @NotNull Integer radioMetros,
		Severidad severidad) {
}
