package cl.ucn.alertacuida.adapters.web.dispositivo;

import cl.ucn.alertacuida.adapters.web.dispositivo.SolicitudSincronizacion.Lectura;
import cl.ucn.alertacuida.adapters.web.dispositivo.SolicitudSincronizacion.Ubicacion;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Lo que llevó al reloj a alertar (esquema Evidencia). Todos los campos son opcionales: cada tipo
 * de alerta usa los suyos. Se guarda como JSON en alerta.evidencia, tal cual.
 *
 * @param lecturas            las que cumplieron la regla (por ejemplo, las 2 consecutivas)
 * @param segundosInmovilidad en las caídas
 * @param celdaMatriz         celda de la matriz FC × SpO2 que aplicó
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvidenciaReloj(
		@Size(max = 100) List<@NotNull @Valid Lectura> lecturas,
		@Min(0) @Max(100) Integer nivelBateria,
		@Min(0) Integer segundosInmovilidad,
		@Size(max = 40) String celdaMatriz,
		@Valid Ubicacion ubicacion) {
}
