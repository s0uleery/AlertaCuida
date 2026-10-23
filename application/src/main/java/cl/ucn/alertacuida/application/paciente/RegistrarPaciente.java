package cl.ucn.alertacuida.application.paciente;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.paciente.modelo.DatosPaciente;
import cl.ucn.alertacuida.application.paciente.puerto.Pacientes;
import cl.ucn.alertacuida.domain.comun.Run;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import java.time.Clock;
import java.util.UUID;

/** Alta de un paciente en el programa. Sin reloj: se vincula después (VinculoReloj). */
public class RegistrarPaciente {

	private final Pacientes pacientes;
	private final HashRun hashRun;
	private final RegistroAuditoria auditoria;
	private final Transacciones transacciones;
	private final Clock reloj;

	/** Recibe los puertos que usa; los arma ConfiguracionPacientesYRelojes. */
	public RegistrarPaciente(Pacientes pacientes, HashRun hashRun, RegistroAuditoria auditoria,
			Transacciones transacciones, Clock reloj) {
		this.pacientes = pacientes;
		this.hashRun = hashRun;
		this.auditoria = auditoria;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/**
	 * @param datos identidad del paciente
	 * @param idUsuario quien lo registra, para la auditoría
	 * @param ip        desde dónde llegó la petición, para la auditoría
	 * @return el identificador del paciente creado
	 * @throws SolicitudRechazada si un dato no cumple las reglas
	 * @throws Conflicto          si ya hay un paciente con ese RUN (activo o dado de baja)
	 */
	public UUID registrar(DatosPaciente datos, UUID idUsuario, String ip) {
		DatosPaciente limpios = ValidacionPaciente.validar(datos, ZonaHoraria.hoy(reloj));
		String runHash = hashRun.de(Run.de(limpios.run()));
		if (pacientes.existeRunHash(runHash, null)) {
			throw new Conflicto("Ya existe un usuario con ese RUN");
		}
		return transacciones.ejecutar(() -> {
			UUID id = pacientes.crear(limpios, runHash);
			auditoria.registrar(idUsuario, "CREAR", "PACIENTE", id.toString(), ip, reloj.instant());
			return id;
		});
	}

}
