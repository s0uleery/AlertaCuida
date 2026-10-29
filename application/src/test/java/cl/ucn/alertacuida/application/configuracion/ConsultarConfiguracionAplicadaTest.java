package cl.ucn.alertacuida.application.configuracion;

import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.application.configuracion.modelo.ConfiguracionAplicada;
import cl.ucn.alertacuida.application.configuracion.modelo.EstadoConfiguracion;
import cl.ucn.alertacuida.application.soporte.AsignacionesEnMemoria;
import cl.ucn.alertacuida.application.soporte.SincronizacionesEnMemoria;
import cl.ucn.alertacuida.application.telemetria.modelo.EstadoInformado;
import cl.ucn.alertacuida.application.telemetria.modelo.LoteRecibido;
import cl.ucn.alertacuida.domain.telemetria.TipoConexion;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Si el reloj de un paciente ya aplica la configuración vigente, sin base de datos. */
class ConsultarConfiguracionAplicadaTest {

	private static final UUID PACIENTE = UUID.randomUUID();
	private static final Instant AYER = Instant.parse("2026-10-24T15:00:00Z");

	private final AsignacionesEnMemoria asignaciones = new AsignacionesEnMemoria();
	private final SincronizacionesEnMemoria sincronizaciones = new SincronizacionesEnMemoria();
	/** La versión vigente del paciente es siempre "v2". */
	private final ConsultarConfiguracionAplicada consultar = new ConsultarConfiguracionAplicada(id -> "v2",
			asignaciones, sincronizaciones);

	/** Sin reloj, o con un reloj que aún no sincroniza, no hay versión del reloj que comparar. */
	@Test
	void sinRelojOSinSincronizar() {
		assertThat(consultar.consultar(PACIENTE).estado()).isEqualTo(EstadoConfiguracion.SIN_RELOJ);

		asignaciones.abrir(UUID.randomUUID(), PACIENTE, AYER, "hash");

		ConfiguracionAplicada sinLotes = consultar.consultar(PACIENTE);
		assertThat(sinLotes.estado()).isEqualTo(EstadoConfiguracion.SIN_SINCRONIZAR);
		assertThat(sinLotes.versionVigente()).isEqualTo("v2");
	}

	/** Pendiente mientras el reloj informa otra versión; al día desde el primer lote con la vigente. */
	@Test
	void pendienteHastaQueElRelojInformaLaVigente() {
		UUID asignacion = asignaciones.abrir(UUID.randomUUID(), PACIENTE, AYER, "hash");
		sincronizar(asignacion, "v1", AYER.plusSeconds(3600));

		ConfiguracionAplicada pendiente = consultar.consultar(PACIENTE);
		assertThat(pendiente.estado()).isEqualTo(EstadoConfiguracion.PENDIENTE);
		assertThat(pendiente.versionReloj()).isEqualTo("v1");

		sincronizar(asignacion, "v2", AYER.plusSeconds(7200));
		sincronizar(asignacion, "v2", AYER.plusSeconds(10800));

		ConfiguracionAplicada alDia = consultar.consultar(PACIENTE);
		assertThat(alDia.estado()).isEqualTo(EstadoConfiguracion.AL_DIA);
		assertThat(alDia.aplicadaDesde()).isEqualTo(AYER.plusSeconds(7200));
		assertThat(alDia.ultimaSincronizacion()).isEqualTo(AYER.plusSeconds(10800));
	}

	/** Solo cuenta el reloj vigente: los lotes de un reloj reemplazado no dicen nada del nuevo. */
	@Test
	void soloCuentaElRelojVigente() {
		UUID anterior = asignaciones.abrir(UUID.randomUUID(), PACIENTE, AYER, "hash-1");
		sincronizar(anterior, "v2", AYER.plusSeconds(3600));
		asignaciones.cerrar(anterior, AYER.plusSeconds(7200));
		asignaciones.abrir(UUID.randomUUID(), PACIENTE, AYER.plusSeconds(7200), "hash-2");

		assertThat(consultar.consultar(PACIENTE).estado()).isEqualTo(EstadoConfiguracion.SIN_SINCRONIZAR);
	}

	/** Guarda un lote vacío del reloj que informa esa versión. */
	private void sincronizar(UUID asignacion, String version, Instant recepcion) {
		sincronizaciones.crear(asignacion, new LoteRecibido(UUID.randomUUID(), recepcion,
				new EstadoInformado(80, true, null, TipoConexion.LTE, "1.0", version), List.of(), List.of(),
				List.of()), recepcion);
	}

}
