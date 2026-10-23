package cl.ucn.alertacuida.application.paciente;

import cl.ucn.alertacuida.application.asignacion.puerto.Asignaciones;
import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.paciente.modelo.Paciente;
import cl.ucn.alertacuida.application.paciente.puerto.Pacientes;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Baja de un paciente del programa (1.2.1). No se borra nada: queda la fecha de baja, se cierra su
 * vínculo con el reloj (lo que revoca el token del reloj) y su historial se conserva.
 */
public class DarDeBajaPaciente {

	private final Pacientes pacientes;
	private final Asignaciones asignaciones;
	private final RegistroAuditoria auditoria;
	private final Transacciones transacciones;
	private final Clock reloj;

	public DarDeBajaPaciente(Pacientes pacientes, Asignaciones asignaciones, RegistroAuditoria auditoria,
			Transacciones transacciones, Clock reloj) {
		this.pacientes = pacientes;
		this.asignaciones = asignaciones;
		this.auditoria = auditoria;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/**
	 * Da de baja con fecha de hoy y, en la misma transacción, cierra su reloj si tenía.
	 *
	 * @param id        paciente
	 * @param idUsuario quien lo da de baja, para la auditoría
	 * @param ip        desde dónde llegó la petición, para la auditoría
	 * @throws NoEncontrado si el paciente no existe
	 * @throws Conflicto    si ya estaba dado de baja
	 */
	public void darDeBaja(UUID id, UUID idUsuario, String ip) {
		Paciente paciente = pacientes.buscar(id).orElseThrow(() -> new NoEncontrado("No existe ese usuario"));
		if (!paciente.activo()) {
			throw new Conflicto("El usuario ya estaba dado de baja");
		}
		Instant ahora = reloj.instant();
		transacciones.ejecutar(() -> {
			asignaciones.vigenteDelPaciente(id).ifPresent(vigente -> {
				asignaciones.cerrar(vigente.idAsignacion(), ahora);
				auditoria.registrar(idUsuario, "MODIFICAR", "ASIGNACION_RELOJ", vigente.idAsignacion().toString(), ip,
						ahora);
			});
			pacientes.darDeBaja(id, ZonaHoraria.hoy(reloj));
			auditoria.registrar(idUsuario, "MODIFICAR", "PACIENTE", id.toString(), ip, ahora);
			return null;
		});
	}

}
