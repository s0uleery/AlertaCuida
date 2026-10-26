package cl.ucn.alertacuida.application.soporte;

import cl.ucn.alertacuida.application.clinica.modelo.AjustesClinicos;
import cl.ucn.alertacuida.application.clinica.modelo.ConfiguracionGuardada;
import cl.ucn.alertacuida.application.clinica.modelo.UmbralGuardado;
import cl.ucn.alertacuida.application.clinica.puerto.PerfilesClinicos;
import cl.ucn.alertacuida.domain.clinica.Umbral;
import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Doble de {@link PerfilesClinicos} para las pruebas unitarias: guarda el historial en listas. */
public final class PerfilesClinicosEnMemoria implements PerfilesClinicos {

	/** Una fila de configuración. */
	private record FilaConfiguracion(UUID idPaciente, ConfiguracionGuardada guardada) {
	}

	/** Una fila de umbral. */
	private record FilaUmbral(UUID idPaciente, TipoLectura variable, UmbralGuardado guardado) {
	}

	private final List<FilaConfiguracion> configuraciones = new ArrayList<>();
	private final List<FilaUmbral> umbrales = new ArrayList<>();

	@Override
	public Optional<ConfiguracionGuardada> configuracionVigente(UUID idPaciente) {
		return configuraciones.stream().filter(fila -> fila.idPaciente().equals(idPaciente))
				.map(FilaConfiguracion::guardada)
				.reduce((anterior, siguiente) -> siguiente);
	}

	@Override
	public void guardarConfiguracion(UUID idPaciente, Instant vigenteDesde, AjustesClinicos ajustes) {
		configuraciones.add(new FilaConfiguracion(idPaciente, new ConfiguracionGuardada(ajustes, vigenteDesde)));
	}

	@Override
	public Optional<UmbralGuardado> umbralVigente(UUID idPaciente, TipoLectura variable) {
		return umbrales.stream()
				.filter(fila -> fila.idPaciente().equals(idPaciente) && fila.variable() == variable)
				.map(FilaUmbral::guardado)
				.reduce((anterior, siguiente) -> siguiente);
	}

	@Override
	public void guardarUmbral(UUID idPaciente, TipoLectura variable, Instant vigenteDesde, Umbral umbral) {
		umbrales.add(new FilaUmbral(idPaciente, variable, new UmbralGuardado(umbral, vigenteDesde)));
	}

	/** @return cuántas filas de historial se guardaron en total */
	public int filas() {
		return configuraciones.size() + umbrales.size();
	}

}
