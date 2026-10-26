package cl.ucn.alertacuida.application.clinica.puerto;

import cl.ucn.alertacuida.application.clinica.modelo.AjustesClinicos;
import cl.ucn.alertacuida.application.clinica.modelo.ConfiguracionGuardada;
import cl.ucn.alertacuida.application.clinica.modelo.UmbralGuardado;
import cl.ucn.alertacuida.domain.clinica.Umbral;
import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Historial de la configuración y los umbrales de cada paciente (tablas configuracion y umbral).
 * Cada cambio agrega una fila; la vigente es la de mayor vigente_desde. El motivo se cifra en el adaptador.
 */
public interface PerfilesClinicos {

	/** @return la configuración vigente, o vacío si el paciente nunca se configuró */
	Optional<ConfiguracionGuardada> configuracionVigente(UUID idPaciente);

	/** Agrega una fila de configuración que rige desde esa fecha. */
	void guardarConfiguracion(UUID idPaciente, Instant vigenteDesde, AjustesClinicos ajustes);

	/** @return la fila vigente del umbral de esa variable, o vacío si nunca se ajustó */
	Optional<UmbralGuardado> umbralVigente(UUID idPaciente, TipoLectura variable);

	/**
	 * Agrega una fila de umbral que rige desde esa fecha.
	 *
	 * @param umbral los límites, o vacío para volver al umbral base sin borrar el historial
	 */
	void guardarUmbral(UUID idPaciente, TipoLectura variable, Instant vigenteDesde, Umbral umbral);

}
