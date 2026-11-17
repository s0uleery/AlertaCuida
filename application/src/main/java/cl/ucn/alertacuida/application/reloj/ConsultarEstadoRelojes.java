package cl.ucn.alertacuida.application.reloj;

import cl.ucn.alertacuida.application.configuracion.VersionVigente;
import cl.ucn.alertacuida.application.reloj.modelo.DatosTecnicosReloj;
import cl.ucn.alertacuida.application.reloj.modelo.EstadoTecnicoReloj;
import cl.ucn.alertacuida.application.reloj.puerto.EstadosTecnicos;
import cl.ucn.alertacuida.domain.reloj.DatosSemaforo;
import cl.ucn.alertacuida.domain.reloj.EstadoSemaforo;
import cl.ucn.alertacuida.domain.reloj.SemaforoReloj;
import cl.ucn.alertacuida.domain.reloj.UmbralesSemaforo;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Estado técnico de todos los relojes, con su semáforo ({@link SemaforoReloj}). La versión de app más nueva es la
 * más alta que informa cualquier reloj, y la configuración está pendiente si la última que informó el reloj no
 * es la vigente de su paciente.
 */
public class ConsultarEstadoRelojes {

	private final EstadosTecnicos estados;
	private final VersionVigente versiones;
	private final UmbralesSemaforo umbrales;
	private final Clock reloj;

	/**
	 * Recibe lo que usa; lo arma ConfiguracionPacientesYRelojes.
	 *
	 * @param versiones la configuración vigente de cada paciente
	 * @param umbrales  los del semáforo, iguales a los de las reglas de ausencia
	 */
	public ConsultarEstadoRelojes(EstadosTecnicos estados, VersionVigente versiones, UmbralesSemaforo umbrales,
			Clock reloj) {
		this.estados = estados;
		this.versiones = versiones;
		this.umbrales = umbrales;
		this.reloj = reloj;
	}

	/** @return el estado técnico de cada reloj registrado */
	public List<EstadoTecnicoReloj> consultar() {
		Instant ahora = reloj.instant();
		List<DatosTecnicosReloj> todos = estados.todos();
		String masNueva = todos.stream().map(DatosTecnicosReloj::versionApp).filter(Objects::nonNull)
				.max(SemaforoReloj::compararVersiones).orElse(null);
		return todos.stream().map(datos -> estado(datos, masNueva, ahora)).toList();
	}

	/** @return el estado de un reloj, con su semáforo */
	private EstadoTecnicoReloj estado(DatosTecnicosReloj datos, String masNueva, Instant ahora) {
		boolean pendiente = configuracionPendiente(datos);
		EstadoSemaforo semaforo = SemaforoReloj.evaluar(new DatosSemaforo(datos.idPaciente() != null,
				datos.inicioVinculo(), datos.ultimaSincronizacion(), datos.nivelBateria(), datos.relojPuesto(),
				datos.versionApp(), pendiente, datos.alertaTecnicaAbierta(), masNueva, datos.vencimientoPlan()),
				umbrales, ahora);
		return new EstadoTecnicoReloj(datos.idDispositivo(), semaforo.color(), semaforo.motivos(),
				datos.ultimaSincronizacion(), datos.nivelBateria(), datos.relojPuesto(), datos.tipoConexion(),
				datos.versionApp(), pendiente);
	}

	/** @return si el reloj informó una configuración distinta de la vigente; sin informarla, no se sabe: no */
	private boolean configuracionPendiente(DatosTecnicosReloj datos) {
		return datos.idPaciente() != null && datos.versionConfiguracion() != null
				&& !datos.versionConfiguracion().equals(versiones.de(datos.idPaciente()));
	}

}
