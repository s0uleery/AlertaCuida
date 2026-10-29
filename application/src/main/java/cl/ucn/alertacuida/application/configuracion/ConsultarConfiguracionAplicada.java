package cl.ucn.alertacuida.application.configuracion;

import cl.ucn.alertacuida.application.asignacion.modelo.AsignacionVigente;
import cl.ucn.alertacuida.application.asignacion.puerto.Asignaciones;
import cl.ucn.alertacuida.application.configuracion.modelo.ConfiguracionAplicada;
import cl.ucn.alertacuida.application.configuracion.modelo.EstadoConfiguracion;
import cl.ucn.alertacuida.application.telemetria.modelo.VersionInformada;
import cl.ucn.alertacuida.application.telemetria.puerto.Sincronizaciones;
import java.util.Optional;
import java.util.UUID;

/**
 * Si el reloj de un paciente ya aplica la configuración vigente, para mostrarlo en su ficha. Así
 * el equipo sabe si un cambio de contactos o de umbrales ya llegó al reloj.
 *
 * <p>Se compara la versión que informó el reloj en su último lote con la que calcula hoy el
 * servidor. Solo cuenta el reloj vigente: los lotes de un reloj anterior no dicen nada del actual.
 */
public class ConsultarConfiguracionAplicada {

	private final VersionVigente versiones;
	private final Asignaciones asignaciones;
	private final Sincronizaciones sincronizaciones;

	/** Recibe lo que usa; lo arma ConfiguracionApiReloj. */
	public ConsultarConfiguracionAplicada(VersionVigente versiones, Asignaciones asignaciones,
			Sincronizaciones sincronizaciones) {
		this.versiones = versiones;
		this.asignaciones = asignaciones;
		this.sincronizaciones = sincronizaciones;
	}

	/**
	 * @return el estado de la configuración en el reloj del paciente
	 * @throws cl.ucn.alertacuida.application.comun.error.NoEncontrado si el paciente no existe
	 */
	public ConfiguracionAplicada consultar(UUID idPaciente) {
		String vigente = versiones.de(idPaciente);
		Optional<AsignacionVigente> asignacion = asignaciones.vigenteDelPaciente(idPaciente);
		if (asignacion.isEmpty()) {
			return new ConfiguracionAplicada(EstadoConfiguracion.SIN_RELOJ, vigente, null, null, null);
		}
		Optional<VersionInformada> informada = sincronizaciones.versionInformada(asignacion.get().idAsignacion());
		if (informada.isEmpty()) {
			return new ConfiguracionAplicada(EstadoConfiguracion.SIN_SINCRONIZAR, vigente, null, null, null);
		}
		VersionInformada ultima = informada.get();
		EstadoConfiguracion estado = vigente.equals(ultima.version())
				? EstadoConfiguracion.AL_DIA
				: EstadoConfiguracion.PENDIENTE;
		return new ConfiguracionAplicada(estado, vigente, ultima.version(), ultima.desde(), ultima.ultima());
	}

}
