package cl.ucn.alertacuida.adapters.web.dispositivo;

import cl.ucn.alertacuida.application.telemetria.modelo.EstadoInformado;
import cl.ucn.alertacuida.application.telemetria.modelo.EventoDescartado;
import cl.ucn.alertacuida.application.telemetria.modelo.LecturaRecibida;
import cl.ucn.alertacuida.application.telemetria.modelo.LoteRecibido;
import cl.ucn.alertacuida.application.telemetria.modelo.UbicacionRecibida;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import cl.ucn.alertacuida.domain.telemetria.TipoConexion;
import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/reloj/v1/sincronizaciones} (esquema Sincronizacion de
 * docs/api-reloj.yaml). Aquí solo se revisa la forma; los rangos de las lecturas y el tamaño del
 * lote los revisa el caso de uso, que descarta lecturas sin rechazar el lote.
 *
 * @param lecturas           opcional: vacía si no hay
 * @param ubicaciones        opcional: solo con el GPS activo
 * @param eventosDescartados opcional: vacía si no hay
 */
public record SolicitudSincronizacion(
		@NotNull UUID eventId,
		@NotNull Instant fechaHoraEnvio,
		@NotNull @Valid Estado estado,
		List<@NotNull @Valid Lectura> lecturas,
		List<@NotNull @Valid Ubicacion> ubicaciones,
		List<@NotNull @Valid Descartado> eventosDescartados) {

	/**
	 * Cómo estaba el reloj.
	 *
	 * @param minutosPuesto        opcional
	 * @param versionConfiguracion opcional: null si aún no recibió ninguna
	 */
	public record Estado(@NotNull Integer nivelBateria, @NotNull Boolean relojPuesto, Integer minutosPuesto,
			@NotNull TipoConexion tipoConexion, @NotNull @Size(min = 1, max = 40) String versionApp,
			@Size(max = 128) String versionConfiguracion) {
	}

	/** Una medición de FC o SpO2. */
	public record Lectura(@NotNull TipoLectura tipo, @NotNull Double valor, @NotNull Instant fechaHoraMedicion) {
	}

	/** Una posición GPS. */
	public record Ubicacion(
			@NotNull @DecimalMin("-90") @DecimalMax("90") Double latitud,
			@NotNull @DecimalMin("-180") @DecimalMax("180") Double longitud,
			@DecimalMin("0") Double precisionMetros,
			@NotNull Instant fechaHoraMedicion) {
	}

	/** Un contador de eventos detectados y no alertados. */
	public record Descartado(@NotNull TipoEvento tipoEvento, @NotNull Integer cantidad) {
	}

	/** @return el lote en el formato del caso de uso; las listas ausentes quedan vacías */
	LoteRecibido lote() {
		EstadoInformado informado = new EstadoInformado(estado.nivelBateria(), estado.relojPuesto(),
				estado.minutosPuesto(), estado.tipoConexion(), estado.versionApp(), estado.versionConfiguracion());
		return new LoteRecibido(eventId, fechaHoraEnvio, informado,
				lista(lecturas).stream().map(l -> new LecturaRecibida(l.tipo(), l.valor(), l.fechaHoraMedicion()))
						.toList(),
				lista(ubicaciones).stream().map(u -> new UbicacionRecibida(u.latitud(), u.longitud(),
						u.precisionMetros(), u.fechaHoraMedicion())).toList(),
				lista(eventosDescartados).stream().map(d -> new EventoDescartado(d.tipoEvento(), d.cantidad()))
						.toList());
	}

	/** @return la lista, o una vacía si el reloj no la envió */
	private static <T> List<T> lista(List<T> valores) {
		return valores == null ? List.of() : valores;
	}

}
