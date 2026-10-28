package cl.ucn.alertacuida.application.reloj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.asignacion.modelo.RelojAutenticado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.soporte.RelojesEnMemoria;
import cl.ucn.alertacuida.domain.reloj.EstadoReloj;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Registro del token de Firebase del reloj, sin base de datos. */
class RegistrarTokenFcmTest {

	private static final Instant AHORA = Instant.parse("2026-10-27T15:00:00Z");

	private final RelojesEnMemoria relojes = new RelojesEnMemoria();
	private final RegistrarTokenFcm registrar = new RegistrarTokenFcm(relojes, Clock.fixed(AHORA, ZoneOffset.UTC));

	/** El token se guarda en el reloj físico, sin espacios a los lados, y el nuevo reemplaza al anterior. */
	@Test
	void guardaYReemplazaElToken() {
		UUID reloj = relojes.agregar("R-001", EstadoReloj.OPERATIVO);
		RelojAutenticado quien = new RelojAutenticado(UUID.randomUUID(), reloj, UUID.randomUUID(), AHORA);

		registrar.registrar(quien, "  token-1 ");
		registrar.registrar(quien, "token-2");

		assertThat(relojes.tokensFcm).containsExactlyEntriesOf(Map.of(reloj, "token-2"));
	}

	/** Un token vacío o demasiado largo se rechaza. */
	@Test
	void rechazaUnTokenVacioOLargo() {
		RelojAutenticado quien = new RelojAutenticado(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AHORA);

		assertThatThrownBy(() -> registrar.registrar(quien, "   ")).isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> registrar.registrar(quien, "x".repeat(RegistrarTokenFcm.LARGO_MAXIMO + 1)))
				.isInstanceOf(SolicitudRechazada.class);
		assertThat(relojes.tokensFcm).isEmpty();
	}

}
