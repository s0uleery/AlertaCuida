package cl.ucn.alertacuida.adapters.web.dispositivo;

import cl.ucn.alertacuida.application.telemetria.modelo.LecturaRechazada;
import cl.ucn.alertacuida.application.telemetria.modelo.ResultadoSincronizacion;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta de {@code POST /api/reloj/v1/sincronizaciones} (esquema RespuestaSincronizacion).
 * Los campos vacíos no se envían.
 *
 * @param desfaseRelojSegundos solo si el reloj está más de 120 s adelantado o atrasado
 * @param versionConfiguracion la vigente; no se envía hasta que exista la configuración del reloj
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RespuestaSincronizacion(UUID idSincronizacion, Instant fechaHoraRecepcion, int lecturasGuardadas,
		int lecturasRepetidas, List<LecturaRechazada> lecturasRechazadas, boolean loteRepetido,
		boolean configuracionNueva, String versionConfiguracion, Long desfaseRelojSegundos) {

	/** @return la respuesta con los nombres del contrato */
	static RespuestaSincronizacion de(ResultadoSincronizacion resultado) {
		return new RespuestaSincronizacion(resultado.idSincronizacion(), resultado.recepcion(),
				resultado.lecturasGuardadas(), resultado.lecturasRepetidas(), resultado.lecturasRechazadas(),
				resultado.loteRepetido(), resultado.configuracionNueva(), resultado.versionConfiguracion(),
				resultado.desfaseRelojSegundos());
	}

}
