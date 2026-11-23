package cl.ucn.alertacuida.application.auditoria.modelo;

import java.time.Instant;
import java.util.UUID;

/**
 * Una fila del registro de auditoría, tal como se muestra al administrador.
 *
 * @param id            identificador de la fila
 * @param fechaHora     cuándo ocurrió
 * @param accion        LEER, CREAR, MODIFICAR, LOGIN, …
 * @param entidad       tipo de objeto; null en los inicios de sesión
 * @param idEntidad     identificador del objeto; null en los inicios de sesión
 * @param detalleJson   datos adicionales en JSON, o null
 * @param idUsuario     quién lo hizo; null en un login fallido con un RUN que no existe
 * @param nombreUsuario nombre de quien lo hizo; null si no hay cuenta
 * @param ip            desde dónde llegó la petición, o null
 */
public record RegistroAuditado(UUID id, Instant fechaHora, String accion, String entidad, String idEntidad,
		String detalleJson, UUID idUsuario, String nombreUsuario, String ip) {
}
