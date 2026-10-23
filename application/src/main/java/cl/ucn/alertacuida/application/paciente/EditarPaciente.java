package cl.ucn.alertacuida.application.paciente;

import cl.ucn.alertacuida.application.asignacion.puerto.Asignaciones;
import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.paciente.modelo.DatosPaciente;
import cl.ucn.alertacuida.application.paciente.puerto.Pacientes;
import cl.ucn.alertacuida.domain.comun.Run;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import java.time.Clock;
import java.util.UUID;

/**
 * Corrige la identidad de un paciente (1.2.1), incluido el RUN si se escribió mal. Se permite
 * también con pacientes dados de baja, para corregir su historial.
 */
public class EditarPaciente {

	private final Pacientes pacientes;
	private final Asignaciones asignaciones;
	private final HashRun hashRun;
	private final RegistroAuditoria auditoria;
	private final Transacciones transacciones;
	private final Clock reloj;

	public EditarPaciente(Pacientes pacientes, Asignaciones asignaciones, HashRun hashRun,
			RegistroAuditoria auditoria, Transacciones transacciones, Clock reloj) {
		this.pacientes = pacientes;
		this.asignaciones = asignaciones;
		this.hashRun = hashRun;
		this.auditoria = auditoria;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/**
	 * @param id        paciente a editar
	 * @param datos     su identidad completa (reemplaza la anterior)
	 * @param idUsuario quien lo edita, para la auditoría
	 * @param ip        desde dónde llegó la petición, para la auditoría
	 * @throws NoEncontrado       si el paciente no existe
	 * @throws SolicitudRechazada si un dato no cumple las reglas
	 * @throws Conflicto          si el RUN es de otro paciente, o si se borra el consentimiento de
	 *                            alguien que tiene reloj
	 */
	public void editar(UUID id, DatosPaciente datos, UUID idUsuario, String ip) {
		pacientes.buscar(id).orElseThrow(() -> new NoEncontrado("No existe ese usuario"));
		DatosPaciente limpios = ValidacionPaciente.validar(datos, ZonaHoraria.hoy(reloj));
		String runHash = hashRun.de(Run.de(limpios.run()));
		if (pacientes.existeRunHash(runHash, id)) {
			throw new Conflicto("Ya existe otro usuario con ese RUN");
		}
		// Sin consentimiento no puede tener reloj (regla de V2): hay que desvincularlo primero
		if (limpios.fechaConsentimiento() == null && asignaciones.vigenteDelPaciente(id).isPresent()) {
			throw new Conflicto("El usuario tiene un reloj vinculado: desvincúlalo antes de quitar el consentimiento");
		}
		transacciones.ejecutar(() -> {
			pacientes.actualizar(id, limpios, runHash);
			auditoria.registrar(idUsuario, "MODIFICAR", "PACIENTE", id.toString(), ip, reloj.instant());
			return null;
		});
	}

}
