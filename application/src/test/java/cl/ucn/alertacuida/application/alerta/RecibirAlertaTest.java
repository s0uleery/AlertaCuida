package cl.ucn.alertacuida.application.alerta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.alerta.modelo.AlertaRecibida;
import cl.ucn.alertacuida.application.alerta.modelo.ResultadoAlerta;
import cl.ucn.alertacuida.application.asignacion.AutenticarReloj;
import cl.ucn.alertacuida.application.asignacion.modelo.RelojAutenticado;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.application.soporte.AlertasEnMemoria;
import cl.ucn.alertacuida.application.soporte.AsignacionesEnMemoria;
import cl.ucn.alertacuida.domain.alerta.EstadoContacto;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Alertas del reloj, cancelaciones y autenticación del reloj, sin base de datos. */
class RecibirAlertaTest {

	private static final Instant AHORA = Instant.parse("2026-10-25T15:00:00Z");
	private static final RelojAutenticado RELOJ = new RelojAutenticado(UUID.randomUUID(), UUID.randomUUID(),
			UUID.randomUUID(), AHORA.minusSeconds(86_400));

	private final AlertasEnMemoria alertas = new AlertasEnMemoria();
	private final RecibirAlerta recibir = new RecibirAlerta(alertas, Clock.fixed(AHORA, ZoneOffset.UTC));

	/** La alerta queda RECIBIDA; reenviarla devuelve la misma, sin duplicarla. */
	@Test
	void unaAlertaReenviadaNoSeDuplica() {
		AlertaRecibida alerta = alerta(UUID.randomUUID(), Severidad.GRAVE, false);

		ResultadoAlerta primera = recibir.recibir(RELOJ, alerta);
		ResultadoAlerta reenvio = recibir.recibir(RELOJ, alerta);

		assertThat(primera.nueva()).isTrue();
		assertThat(primera.respuesta().estado().estado()).isEqualTo(EstadoContacto.RECIBIDA);
		assertThat(primera.respuesta().estado().detenerEscalamiento()).isFalse();
		assertThat(reenvio.nueva()).isFalse();
		assertThat(reenvio.respuesta().idAlerta()).isEqualTo(primera.respuesta().idAlerta());
		assertThat(alertas.filas).hasSize(1);
	}

	/** Solo la leve espera al día hábil: una media marcada así se guarda sin la marca, no se rechaza. */
	@Test
	void soloLaLeveEsperaAlDiaHabil() {
		ResultadoAlerta leve = recibir.recibir(RELOJ, alerta(UUID.randomUUID(), Severidad.LEVE, true));
		ResultadoAlerta media = recibir.recibir(RELOJ, alerta(UUID.randomUUID(), Severidad.MEDIA, true));

		assertThat(leve.respuesta().estado().estado()).isEqualTo(EstadoContacto.PENDIENTE_DIA_HABIL);
		assertThat(media.respuesta().estado().estado()).isEqualTo(EstadoContacto.RECIBIDA);
	}

	/** Una cancelación queda CANCELADA y detiene el escalamiento; el mismo eventId no puede ser alerta. */
	@Test
	void laCancelacionQuedaCanceladaYNoSeConfundeConUnaAlerta() {
		UUID eventId = UUID.randomUUID();
		AlertaRecibida cancelacion = new AlertaRecibida(eventId, TipoEvento.CAIDA, Severidad.MEDIA, "v1",
				AHORA.minusSeconds(40), null, false, AHORA.minusSeconds(20), null);

		ResultadoAlerta resultado = recibir.registrarCancelacion(RELOJ, cancelacion);

		assertThat(resultado.respuesta().estado().estado()).isEqualTo(EstadoContacto.CANCELADA);
		assertThat(resultado.respuesta().estado().detenerEscalamiento()).isTrue();
		assertThatThrownBy(() -> recibir.recibir(RELOJ, alerta(eventId, Severidad.MEDIA, false)))
				.isInstanceOf(Conflicto.class);
	}

	/** El reloj no envía silencios de datos, y toda alerta trae la configuración con que se evaluó. */
	@Test
	void tipoDelServidorOSinConfiguracionSeRechazan() {
		AlertaRecibida silencio = new AlertaRecibida(UUID.randomUUID(), TipoEvento.SILENCIO_DATOS, Severidad.MEDIA,
				"v1", AHORA, AHORA, false, null, null);
		AlertaRecibida sinConfiguracion = new AlertaRecibida(UUID.randomUUID(), TipoEvento.FC, Severidad.MEDIA, " ",
				AHORA, AHORA, false, null, null);

		assertThatThrownBy(() -> recibir.recibir(RELOJ, silencio)).isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> recibir.recibir(RELOJ, sinConfiguracion)).isInstanceOf(SolicitudRechazada.class);
	}

	/** Un reloj solo ve el estado de sus alertas: la de otro reloj responde "no encontrada". */
	@Test
	void unRelojSoloConsultaSusAlertas() {
		UUID eventId = UUID.randomUUID();
		recibir.recibir(RELOJ, alerta(eventId, Severidad.GRAVE, false));
		RelojAutenticado otro = new RelojAutenticado(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AHORA);

		assertThat(recibir.estado(RELOJ, eventId).eventId()).isEqualTo(eventId);
		assertThatThrownBy(() -> recibir.estado(otro, eventId)).isInstanceOf(NoEncontrado.class);
		assertThatThrownBy(() -> recibir.recibir(otro, alerta(eventId, Severidad.GRAVE, false)))
				.isInstanceOf(Conflicto.class);
	}

	/** El reloj se reconoce solo con el token de un vínculo vigente: al cerrarlo, el token deja de servir. */
	@Test
	void elTokenSoloSirveMientrasElVinculoEsteVigente() {
		AsignacionesEnMemoria asignaciones = new AsignacionesEnMemoria();
		SecretosUnSoloUso secretos = new SecretosUnSoloUso() {
			@Override
			public String nuevo() {
				return "token";
			}

			@Override
			public String hash(String secreto) {
				return "h-" + secreto;
			}
		};
		AutenticarReloj autenticar = new AutenticarReloj(asignaciones, secretos);
		UUID vinculo = asignaciones.abrir(UUID.randomUUID(), UUID.randomUUID(), AHORA, "h-token");

		assertThat(autenticar.autenticar("token")).get().extracting(RelojAutenticado::idAsignacion)
				.isEqualTo(vinculo);
		assertThat(autenticar.autenticar("otro")).isEmpty();
		assertThat(autenticar.autenticar(" ")).isEmpty();
		asignaciones.cerrar(vinculo, AHORA.plusSeconds(60));
		assertThat(autenticar.autenticar("token")).isEmpty();
	}

	/** @return una caída enviada al terminar la ventana de 30 segundos */
	private static AlertaRecibida alerta(UUID eventId, Severidad severidad, boolean esperaDiaHabil) {
		return new AlertaRecibida(eventId, TipoEvento.CAIDA, severidad, "v1", AHORA.minusSeconds(40),
				AHORA.minusSeconds(10), esperaDiaHabil, null, "{\"segundosInmovilidad\":60}");
	}

}
