package cl.ucn.alertacuida.application.auditoria.puerto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Quién cambió qué y cuándo, sin datos personales (registro_auditoria). */
public interface RegistroAuditoria {

	/**
	 * @param accion  CREAR, MODIFICAR, … (valores de registro_auditoria.accion)
	 * @param entidad USUARIO, PACIENTE, … (valores de registro_auditoria.entidad)
	 * @param detalle qué cambió, por ejemplo el rol anterior y el nuevo; vacío si no hace falta. Nunca
	 *                datos personales: la auditoría no se cifra
	 */
	void registrar(UUID idUsuario, String accion, String entidad, String idEntidad, String ip, Instant fechaHora,
			Map<String, Object> detalle);

	/**
	 * Lo mismo, sin detalle: basta con saber quién tocó qué objeto.
	 *
	 * @param accion  CREAR, MODIFICAR, … (valores de registro_auditoria.accion)
	 * @param entidad USUARIO, PACIENTE, … (valores de registro_auditoria.entidad)
	 */
	default void registrar(UUID idUsuario, String accion, String entidad, String idEntidad, String ip,
			Instant fechaHora) {
		registrar(idUsuario, accion, entidad, idEntidad, ip, fechaHora, null);
	}

}
