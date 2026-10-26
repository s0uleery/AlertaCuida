package cl.ucn.alertacuida.application.telemetria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.asignacion.modelo.RelojAutenticado;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.soporte.SincronizacionesEnMemoria;
import cl.ucn.alertacuida.application.telemetria.error.LoteGrande;
import cl.ucn.alertacuida.application.telemetria.modelo.EstadoInformado;
import cl.ucn.alertacuida.application.telemetria.modelo.EventoDescartado;
import cl.ucn.alertacuida.application.telemetria.modelo.LecturaRechazada;
import cl.ucn.alertacuida.application.telemetria.modelo.LecturaRecibida;
import cl.ucn.alertacuida.application.telemetria.modelo.LoteRecibido;
import cl.ucn.alertacuida.application.telemetria.modelo.MotivoRechazo;
import cl.ucn.alertacuida.application.telemetria.modelo.ResultadoSincronizacion;
import cl.ucn.alertacuida.application.telemetria.modelo.UbicacionRecibida;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import cl.ucn.alertacuida.domain.telemetria.TipoConexion;
import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** Recepción de los lotes del reloj, sin base de datos. */
class RecibirSincronizacionTest {

	private static final Instant AHORA = Instant.parse("2026-10-25T15:00:00Z");
	/** El reloj se entregó a este paciente hace 3 días. */
	private static final RelojAutenticado RELOJ = new RelojAutenticado(UUID.randomUUID(), UUID.randomUUID(),
			UUID.randomUUID(), AHORA.minus(Duration.ofDays(3)));

	private final SincronizacionesEnMemoria guardadas = new SincronizacionesEnMemoria();
	private final Transacciones enTransaccion = Supplier::get;
	private final RecibirSincronizacion recibir = new RecibirSincronizacion(guardadas, enTransaccion,
			Clock.fixed(AHORA, ZoneOffset.UTC));

	/** Guarda el estado, las lecturas válidas, las ubicaciones y los contadores (sumados por tipo). */
	@Test
	void guardaElLoteCompleto() {
		LoteRecibido lote = lote(UUID.randomUUID(), AHORA, List.of(fc(72, 60), spo2(96, 60)),
				List.of(new EventoDescartado(TipoEvento.FC, 1), new EventoDescartado(TipoEvento.FC, 2)));

		ResultadoSincronizacion resultado = recibir.recibir(RELOJ, lote);

		assertThat(resultado.lecturasGuardadas()).isEqualTo(2);
		assertThat(resultado.lecturasRechazadas()).isEmpty();
		assertThat(resultado.loteRepetido()).isFalse();
		assertThat(resultado.desfaseRelojSegundos()).isNull();
		assertThat(guardadas.ubicaciones).hasSize(1);
		assertThat(guardadas.descartados).containsExactly(new EventoDescartado(TipoEvento.FC, 3));
	}

	/** Una lectura imposible, futura o de antes de entregar el reloj se descarta sola, con su posición. */
	@Test
	void descartaLecturasMalasSinRechazarElLote() {
		List<LecturaRecibida> lecturas = List.of(fc(72, 60), fc(400, 30), spo2(97, -10),
				fc(70, 60 * 24 * 4));

		ResultadoSincronizacion resultado = recibir.recibir(RELOJ, lote(UUID.randomUUID(), AHORA, lecturas, List.of()));

		assertThat(resultado.lecturasGuardadas()).isEqualTo(1);
		assertThat(resultado.lecturasRechazadas()).containsExactly(
				new LecturaRechazada(1, MotivoRechazo.FUERA_DE_RANGO),
				new LecturaRechazada(2, MotivoRechazo.HORA_FUTURA),
				new LecturaRechazada(3, MotivoRechazo.ANTERIOR_A_LA_ASIGNACION));
	}

	/** Reenviar el lote no guarda nada nuevo, y una lectura ya enviada en otro lote cuenta como repetida. */
	@Test
	void unLoteReenviadoOLecturasRepetidasNoSeDuplican() {
		UUID eventId = UUID.randomUUID();
		ResultadoSincronizacion primero = recibir.recibir(RELOJ, lote(eventId, AHORA, List.of(fc(72, 60)), List.of()));

		ResultadoSincronizacion reenvio = recibir.recibir(RELOJ, lote(eventId, AHORA, List.of(fc(72, 60)), List.of()));
		ResultadoSincronizacion otro = recibir.recibir(RELOJ,
				lote(UUID.randomUUID(), AHORA, List.of(fc(72, 60), fc(75, 30)), List.of()));

		assertThat(reenvio.loteRepetido()).isTrue();
		assertThat(reenvio.idSincronizacion()).isEqualTo(primero.idSincronizacion());
		assertThat(reenvio.lecturasGuardadas()).isEqualTo(1);
		assertThat(otro.lecturasGuardadas()).isEqualTo(1);
		assertThat(otro.lecturasRepetidas()).isEqualTo(1);
		assertThat(guardadas.lotes).hasSize(2);
	}

	/** Un eventId que ya usó otro reloj se rechaza (409). */
	@Test
	void unEventIdDeOtroRelojSeRechaza() {
		UUID eventId = UUID.randomUUID();
		recibir.recibir(RELOJ, lote(eventId, AHORA, List.of(), List.of()));
		RelojAutenticado otro = new RelojAutenticado(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				AHORA.minus(Duration.ofDays(1)));

		assertThatThrownBy(() -> recibir.recibir(otro, lote(eventId, AHORA, List.of(), List.of())))
				.isInstanceOf(Conflicto.class);
	}

	/** Con más de 2 minutos de diferencia, la respuesta informa el desfase del reloj. */
	@Test
	void informaElDesfaseDelReloj() {
		ResultadoSincronizacion resultado = recibir.recibir(RELOJ,
				lote(UUID.randomUUID(), AHORA.minusSeconds(300), List.of(), List.of()));

		assertThat(resultado.desfaseRelojSegundos()).isEqualTo(-300);
	}

	/** Un estado imposible o un lote demasiado grande se rechazan enteros. */
	@Test
	void estadoImposibleOLoteGrandeSeRechazan() {
		LoteRecibido bateriaImposible = new LoteRecibido(UUID.randomUUID(), AHORA,
				new EstadoInformado(150, true, 120, TipoConexion.LTE, "1.0", "v1"), List.of(), List.of(), List.of());
		LoteRecibido silencio = lote(UUID.randomUUID(), AHORA, List.of(),
				List.of(new EventoDescartado(TipoEvento.SILENCIO_DATOS, 1)));
		LoteRecibido grande = lote(UUID.randomUUID(), AHORA, Collections.nCopies(2001, fc(72, 60)), List.of());

		assertThatThrownBy(() -> recibir.recibir(RELOJ, bateriaImposible)).isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> recibir.recibir(RELOJ, silencio)).isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> recibir.recibir(RELOJ, grande)).isInstanceOf(LoteGrande.class);
		assertThat(guardadas.lotes).isEmpty();
	}

	/** @return un lote con una ubicación y el estado de un reloj normal */
	private static LoteRecibido lote(UUID eventId, Instant envio, List<LecturaRecibida> lecturas,
			List<EventoDescartado> descartados) {
		return new LoteRecibido(eventId, envio, new EstadoInformado(80, true, 120, TipoConexion.LTE, "1.0", "v1"),
				lecturas, List.of(new UbicacionRecibida(-23.65, -70.4, 10.0, AHORA.minusSeconds(600))), descartados);
	}

	/** @return una lectura de FC medida hace tantos minutos (negativo = en el futuro) */
	private static LecturaRecibida fc(double valor, long minutosAtras) {
		return new LecturaRecibida(TipoLectura.FC, valor, AHORA.minus(Duration.ofMinutes(minutosAtras)));
	}

	/** @return una lectura de SpO2 medida hace tantos minutos (negativo = en el futuro) */
	private static LecturaRecibida spo2(double valor, long minutosAtras) {
		return new LecturaRecibida(TipoLectura.SPO2, valor, AHORA.minus(Duration.ofMinutes(minutosAtras)));
	}

}
