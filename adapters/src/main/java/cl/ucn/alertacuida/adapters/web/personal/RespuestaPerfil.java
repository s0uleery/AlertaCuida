package cl.ucn.alertacuida.adapters.web.personal;

import cl.ucn.alertacuida.application.personal.modelo.EstadoCuenta;
import cl.ucn.alertacuida.application.personal.modelo.PerfilEquipo;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila de {@code GET /api/personal}: una cuenta del equipo. Sin el RUN ni el teléfono, que van cifrados.
 *
 * @param rol           código del rol
 * @param ultimoAcceso  último inicio de sesión; vacío si nunca ha entrado
 * @param recibeAvisos  si recibe por correo los avisos de las alertas técnicas
 * @param tieneTelefono si tiene celular con WhatsApp: si no, el dashboard lo pide al pasarla a COORDINADOR
 */
public record RespuestaPerfil(UUID id, String nombre, String cargo, String correo, String rol, EstadoCuenta estado,
		Instant ultimoAcceso, boolean recibeAvisos, boolean tieneTelefono) {

	/** @return la cuenta en el formato del endpoint */
	static RespuestaPerfil de(PerfilEquipo perfil) {
		return new RespuestaPerfil(perfil.id(), perfil.nombre(), perfil.cargo(), perfil.correo(), perfil.codigoRol(),
				perfil.estado(), perfil.ultimoAcceso(), perfil.recibeAvisos(), perfil.tieneTelefono());
	}

}
