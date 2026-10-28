package cl.ucn.alertacuida.adapters.web.dispositivo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PUT /api/reloj/v1/token-fcm}.
 *
 * @param token el token que Firebase le dio al reloj
 */
public record SolicitudTokenFcm(@NotBlank @Size(max = 4096) String token) {
}
