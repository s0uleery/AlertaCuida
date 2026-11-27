package cl.ucn.alertacuida.application.aviso;

import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.application.aviso.modelo.BotonInteractivo;
import cl.ucn.alertacuida.application.aviso.modelo.ConversacionWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.EtapaConversacion;
import cl.ucn.alertacuida.application.aviso.modelo.MensajeSalientePendiente;
import cl.ucn.alertacuida.application.aviso.modelo.NivelConversacion;
import cl.ucn.alertacuida.application.aviso.modelo.PoliticaConversacionWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.ResultadoCierreWhatsapp;
import cl.ucn.alertacuida.application.aviso.puerto.ConversacionesWhatsapp;
import cl.ucn.alertacuida.application.respuesta.modelo.RespuestaContacto;
import cl.ucn.alertacuida.domain.alerta.EstadoContacto;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de los temporizadores independientes de WhatsApp (decisiones del 26-11-2026):
 * <ul>
 * <li>10 minutos de límite tras "Llamaré al paciente".</li>
 * <li>Recordatorios de llegada independientes: LEVE 30 min × 5 (2 h 30 min), MEDIA 15 min × 5 (1 h 15 min),
 * GRAVE 5 min × 8 (40 min). Al agotarse la paciencia, expira y notifica {@code CAMBIO_ESTADO_ALERTA} al reloj.</li>
 * </ul>
 */
class VigilarConversacionesWhatsappTest {

	private static final Instant T0 = Instant.parse("2026-11-26T15:00:00Z");

	/** Si quien llamó al paciente no responde en 10 min, expira y avisa al reloj para escalar. */
	@Test
	void plazoDeLlamadaVencidoExpiraYEscala() {
		RepoVigilancia repo = new RepoVigilancia(conversacion(Severidad.MEDIA,
				EtapaConversacion.ESPERANDO_ESTADO_LLAMADA, T0.plus(Duration.ofMinutes(10))));

		vigilarEn(repo, T0.plus(Duration.ofMinutes(10)));

		assertThat(repo.actual.etapa()).isEqualTo(EtapaConversacion.EXPIRADA);
		assertThat(repo.avisosReloj).isEqualTo(1);
		assertThat(repo.mensajes).singleElement().asString().contains("Escalaremos");
	}

	/** LEVE: cada 30 min, 4 recordatorios y al 5.º intervalo (2 h 30 min) expira y escala. */
	@Test
	void recordatoriosLeveCorrenCada30MinHasta2Horas30Min() {
		verificarCiclo(Severidad.LEVE, Duration.ofMinutes(30), 5);
	}

	/** MEDIA: cada 15 min, 4 recordatorios y al 5.º intervalo (1 h 15 min) expira y escala. */
	@Test
	void recordatoriosMediaCorrenCada15MinHasta1Hora15Min() {
		verificarCiclo(Severidad.MEDIA, Duration.ofMinutes(15), 5);
	}

	/** GRAVE: cada 5 min, 7 recordatorios y al 8.º intervalo (40 min) expira y escala. */
	@Test
	void recordatoriosGraveCorrenCada5MinHasta40Min() {
		verificarCiclo(Severidad.GRAVE, Duration.ofMinutes(5), 8);
	}

	/** Recorre todos los intervalos de una severidad y verifica recordatorios + escalamiento final. */
	private void verificarCiclo(Severidad severidad, Duration intervalo, int maximo) {
		RepoVigilancia repo = new RepoVigilancia(conversacion(severidad, EtapaConversacion.ESPERANDO_LLEGADA,
				T0.plus(intervalo)));

		for (int paso = 1; paso < maximo; paso++) {
			Instant momento = T0.plus(intervalo.multipliedBy(paso));
			vigilarEn(repo, momento);
			assertThat(repo.actual.etapa()).isEqualTo(EtapaConversacion.ESPERANDO_LLEGADA);
			assertThat(repo.actual.recordatoriosEnviados()).isEqualTo(paso);
			assertThat(repo.actual.proximoVencimiento()).isEqualTo(momento.plus(intervalo));
			assertThat(repo.avisosReloj).isZero();
		}

		// Al cumplirse el último intervalo (LEVE 2h30m, MEDIA 1h15m, GRAVE 40m), expira y avisa al reloj
		vigilarEn(repo, T0.plus(intervalo.multipliedBy(maximo)));
		assertThat(repo.actual.etapa()).isEqualTo(EtapaConversacion.EXPIRADA);
		assertThat(repo.avisosReloj).isEqualTo(1);
		assertThat(repo.mensajes).hasSize(maximo);
		assertThat(repo.mensajes.getLast()).contains("Escalaremos");
	}

	/** Ejecuta una pasada del vigilante en el instante indicado. */
	private static void vigilarEn(RepoVigilancia repo, Instant ahora) {
		new VigilarConversacionesWhatsapp(repo, PoliticaConversacionWhatsapp.POR_DEFECTO, Supplier::get,
				Clock.fixed(ahora, ZoneOffset.UTC)).vigilar();
	}

	/** @return una conversación activa con ventana de 24 h abierta */
	private static ConversacionWhatsapp conversacion(Severidad severidad, EtapaConversacion etapa,
			Instant vencimiento) {
		return new ConversacionWhatsapp(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 142L, "Rosa",
				"+56987654321", severidad, EstadoContacto.CONFIRMADA, true, NivelConversacion.NIVEL_2_TERCERO, etapa,
				"wamid.1", 0, vencimiento, T0.plus(Duration.ofHours(24)));
	}

	/** Repositorio mínimo en memoria para probar {@link VigilarConversacionesWhatsapp}. */
	private static final class RepoVigilancia implements ConversacionesWhatsapp {

		ConversacionWhatsapp actual;
		int avisosReloj;
		final List<String> mensajes = new ArrayList<>();

		RepoVigilancia(ConversacionWhatsapp actual) {
			this.actual = actual;
		}

		@Override
		public List<ConversacionWhatsapp> vencidas(Instant ahora) {
			if (actual.proximoVencimiento() != null && !actual.proximoVencimiento().isAfter(ahora)) {
				return List.of(actual);
			}
			return List.of();
		}

		@Override
		public void registrarRecordatorio(UUID idConversacion, int recordatoriosEnviados, Instant proximoVencimiento,
				Instant ahora) {
			actual = new ConversacionWhatsapp(actual.idConversacion(), actual.idIntento(), actual.idAlerta(),
					actual.numeroAlerta(), actual.aliasPaciente(), actual.telefonoReloj(), actual.severidad(),
					actual.estadoAlerta(), actual.delReloj(), actual.nivel(), actual.etapa(), actual.ultimoWamid(),
					recordatoriosEnviados, proximoVencimiento, actual.ventana24hHasta());
		}

		@Override
		public void cerrarConversacion(UUID idConversacion, EtapaConversacion etapaFinal, Instant ahora) {
			actual = new ConversacionWhatsapp(actual.idConversacion(), actual.idIntento(), actual.idAlerta(),
					actual.numeroAlerta(), actual.aliasPaciente(), actual.telefonoReloj(), actual.severidad(),
					actual.estadoAlerta(), actual.delReloj(), actual.nivel(), etapaFinal, actual.ultimoWamid(),
					actual.recordatoriosEnviados(), null, actual.ventana24hHasta());
		}

		@Override
		public void dejarAvisoCambioEstadoReloj(UUID idAlerta) {
			avisosReloj++;
		}

		@Override
		public void encolarMensajeSaliente(UUID idConversacion, String cuerpo, List<BotonInteractivo> botones,
				Instant ahora) {
			mensajes.add(cuerpo);
		}

		@Override
		public void crear(UUID idIntento, UUID idAlerta, NivelConversacion nivel, String wamid, Instant ahora) {
		}

		@Override
		public boolean registrarMensajeProcesado(String idMensajeMeta, Instant ahora) {
			return true;
		}

		@Override
		public Optional<ConversacionWhatsapp> buscarPorWamid(String wamid) {
			return Optional.empty();
		}

		@Override
		public boolean tomar(UUID idConversacion, EtapaConversacion nuevaEtapa, Instant proximoVencimiento,
				Instant ventana24h, Instant ahora) {
			return false;
		}

		@Override
		public boolean avanzar(UUID idConversacion, EtapaConversacion etapaEsperada, EtapaConversacion nuevaEtapa,
				Instant proximoVencimiento, Instant ventana24h, Instant ahora) {
			return false;
		}

		@Override
		public void refrescarVentana(UUID idConversacion, Instant ventana24h, Instant ahora) {
		}

		@Override
		public void registrarRespuestaIntento(UUID idIntento, RespuestaContacto respuesta, Instant ahora) {
		}

		@Override
		public void registrarLlegadaIntento(UUID idIntento, Instant ahora) {
		}

		@Override
		public boolean cerrarAlertaParcial(UUID idAlerta, ResultadoCierreWhatsapp resultado, Instant ahora) {
			return false;
		}

		@Override
		public List<MensajeSalientePendiente> mensajesSalientesPendientes(Instant ahora) {
			return List.of();
		}

		@Override
		public void marcarMensajeSalienteEnviado(UUID idMensaje, UUID idConversacion, String wamid, Instant ahora) {
		}

		@Override
		public void reprogramarMensajeSaliente(UUID idMensaje, int reintentos, Instant proximo) {
		}

		@Override
		public void marcarMensajeSalienteFallido(UUID idMensaje, String motivo, Instant ahora) {
		}

	}

}
