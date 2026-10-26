package cl.ucn.alertacuida.application.telemetria.modelo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Una sincronización del reloj: su estado y lo acumulado desde la anterior. Puede traer lecturas
 * ya enviadas, porque el reloj guarda 14 días y reenvía si no recibió respuesta.
 *
 * @param eventId lo genera el reloj; repetirlo no duplica nada
 * @param envio   hora del reloj al armar el lote
 */
public record LoteRecibido(UUID eventId, Instant envio, EstadoInformado estado, List<LecturaRecibida> lecturas,
		List<UbicacionRecibida> ubicaciones, List<EventoDescartado> descartados) {
}
