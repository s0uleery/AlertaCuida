package cl.ucn.alertacuida.application.auditoria.puerto;

import java.time.Instant;
import java.util.UUID;

/** Quién cambió qué y cuándo, sin datos personales (registro_auditoria). */
public interface RegistroAuditoria {

	/**
	 * @param accion  CREAR, MODIFICAR, … (valores de registro_auditoria.accion)
	 * @param entidad USUARIO, PACIENTE, … (valores de registro_auditoria.entidad)
	 */
	void registrar(UUID idUsuario, String accion, String entidad, String idEntidad, String ip, Instant fechaHora);

}
