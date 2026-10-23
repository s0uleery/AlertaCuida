package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.application.asignacion.VinculoReloj;
import cl.ucn.alertacuida.application.asignacion.puerto.Asignaciones;
import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.paciente.ConsultarPacientes;
import cl.ucn.alertacuida.application.paciente.DarDeBajaPaciente;
import cl.ucn.alertacuida.application.paciente.EditarPaciente;
import cl.ucn.alertacuida.application.paciente.RegistrarPaciente;
import cl.ucn.alertacuida.application.paciente.puerto.Pacientes;
import cl.ucn.alertacuida.application.reloj.ConsultarRelojes;
import cl.ucn.alertacuida.application.reloj.EditarReloj;
import cl.ucn.alertacuida.application.reloj.RegistrarReloj;
import cl.ucn.alertacuida.application.reloj.puerto.Relojes;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Casos de uso de pacientes, relojes y el vínculo entre ambos (1.2), unidos con sus adaptadores.
 * Igual que {@link ConfiguracionCasosDeUso}, que tiene los del inicio de sesión y el personal.
 */
@Configuration
public class ConfiguracionPacientesYRelojes {

	/** Lista y ficha de pacientes. */
	@Bean
	ConsultarPacientes consultarPacientes(Pacientes pacientes, Asignaciones asignaciones, Clock reloj) {
		return new ConsultarPacientes(pacientes, asignaciones, reloj);
	}

	/** Alta de pacientes (1.2.1). */
	@Bean
	RegistrarPaciente registrarPaciente(Pacientes pacientes, HashRun hashRun, RegistroAuditoria auditoria,
			Transacciones transacciones, Clock reloj) {
		return new RegistrarPaciente(pacientes, hashRun, auditoria, transacciones, reloj);
	}

	/** Edición de pacientes (1.2.1). */
	@Bean
	EditarPaciente editarPaciente(Pacientes pacientes, Asignaciones asignaciones, HashRun hashRun,
			RegistroAuditoria auditoria, Transacciones transacciones, Clock reloj) {
		return new EditarPaciente(pacientes, asignaciones, hashRun, auditoria, transacciones, reloj);
	}

	/** Baja de pacientes, que cierra su reloj (1.2.1). */
	@Bean
	DarDeBajaPaciente darDeBajaPaciente(Pacientes pacientes, Asignaciones asignaciones, RegistroAuditoria auditoria,
			Transacciones transacciones, Clock reloj) {
		return new DarDeBajaPaciente(pacientes, asignaciones, auditoria, transacciones, reloj);
	}

	/** Inventario de relojes. */
	@Bean
	ConsultarRelojes consultarRelojes(Relojes relojes) {
		return new ConsultarRelojes(relojes);
	}

	/** Registro de relojes (1.2.2). */
	@Bean
	RegistrarReloj registrarReloj(Relojes relojes, RegistroAuditoria auditoria, Transacciones transacciones,
			Clock reloj) {
		return new RegistrarReloj(relojes, auditoria, transacciones, reloj);
	}

	/** Edición de relojes. */
	@Bean
	EditarReloj editarReloj(Relojes relojes, Asignaciones asignaciones, RegistroAuditoria auditoria,
			Transacciones transacciones, Clock reloj) {
		return new EditarReloj(relojes, asignaciones, auditoria, transacciones, reloj);
	}

	/** Vincular, reemplazar y desvincular relojes (1.2.3 y 1.2.4). */
	@Bean
	VinculoReloj vinculoReloj(Asignaciones asignaciones, Pacientes pacientes, Relojes relojes,
			SecretosUnSoloUso secretos, RegistroAuditoria auditoria, Transacciones transacciones, Clock reloj) {
		return new VinculoReloj(asignaciones, pacientes, relojes, secretos, auditoria, transacciones, reloj);
	}

}
