package cl.ucn.alertacuida.application.escalamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.alerta.modelo.AlertaGuardada;
import cl.ucn.alertacuida.application.alerta.modelo.EstadoAlerta;
import cl.ucn.alertacuida.application.alerta.puerto.AlertasDelReloj;
import cl.ucn.alertacuida.application.asignacion.modelo.RelojAutenticado;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.contacto.modelo.ContactoDelPaciente;
import cl.ucn.alertacuida.application.contacto.modelo.Persona;
import cl.ucn.alertacuida.application.contacto.modelo.Vinculo;
import cl.ucn.alertacuida.application.contacto.puerto.ListaDelPaciente;
import cl.ucn.alertacuida.application.escalamiento.modelo.IntentoExistente;
import cl.ucn.alertacuida.application.escalamiento.modelo.MotivoRechazo;
import cl.ucn.alertacuida.application.escalamiento.modelo.RespuestaAviso;
import cl.ucn.alertacuida.application.escalamiento.modelo.ResultadoAviso;
import cl.ucn.alertacuida.application.escalamiento.modelo.SolicitudAviso;
import cl.ucn.alertacuida.application.escalamiento.puerto.IntentosAviso;
import cl.ucn.alertacuida.domain.alerta.EstadoContacto;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import cl.ucn.alertacuida.domain.contacto.Canal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Solicitud de aviso del reloj (POST /alertas/{eventId}/avisos), sin base de datos. Las reglas salen de
 * docs/api-reloj.yaml (SolicitudAviso, RespuestaAviso) y de docs/api-reloj.md:
 * <ul>
 * <li>un rechazo no es un error: 200 con RECHAZADO y el motivo, y el reloj pasa al siguiente;</li>
 * <li>no se envía dos veces: ni con el mismo eventId ni con el mismo contacto, canal y ronda;</li>
 * <li>si la alerta ya terminó (confirmada, resuelta o cancelada), RECHAZADO con ALERTA_TERMINADA;</li>
 * <li>WhatsApp solo con opt-in vigente; cada respuesta trae el estado de la alerta.</li>
 * </ul>
 */
class SolicitarAvisoTest {

	private static final Instant AHORA = Instant.parse("2026-11-13T15:00:00Z");
	private static final UUID PACIENTE = UUID.randomUUID();
	private static final UUID ASIGNACION = UUID.randomUUID();
	private static final RelojAutenticado RELOJ = new RelojAutenticado(ASIGNACION, UUID.randomUUID(), PACIENTE,
			AHORA.minusSeconds(86_400));
	private static final UUID EVENT_ALERTA = UUID.randomUUID();
	private static final UUID ID_ALERTA = UUID.randomUUID();

	/** Hija: WhatsApp con opt-in y correo. */
	private static final UUID HIJA = UUID.randomUUID();
	/** Vecino: WhatsApp sin opt-in, sin correo. */
	private static final UUID VECINO = UUID.randomUUID();
	/** Sobrino: solo correo, desactivado. */
	private static final UUID SOBRINO = UUID.randomUUID();
	/** Amiga: WhatsApp con opt-in revocado, sin correo. */
	private static final UUID AMIGA = UUID.randomUUID();

	private final AlertasEnMemoria alertas = new AlertasEnMemoria();
	private final IntentosEnMemoria intentos = new IntentosEnMemoria();
	private SolicitarAviso solicitar;

	/** Una caída grave recibida y la lista de contactos del paciente. */
	@BeforeEach
	void preparar() {
		alertas.guardar(new AlertaGuardada(ID_ALERTA, ASIGNACION, 142, TipoEvento.CAIDA, Severidad.GRAVE, false,
				false), EVENT_ALERTA, EstadoContacto.RECIBIDA);
		List<ContactoDelPaciente> lista = List.of(
				contacto(HIJA, "+56911111111", "hija@correo.cl", AHORA.minusSeconds(999), null, true),
				contacto(VECINO, "+56922222222", null, null, null, true),
				contacto(SOBRINO, null, "sobrino@correo.cl", null, null, false),
				contacto(AMIGA, "+56933333333", null, AHORA.minusSeconds(999), AHORA.minusSeconds(9), true));
		ListaDelPaciente contactos = paciente -> paciente.equals(PACIENTE) ? lista : List.of();
		Transacciones enTransaccion = Supplier::get;
		solicitar = new SolicitarAviso(alertas, contactos, intentos, enTransaccion,
				Clock.fixed(AHORA, ZoneOffset.UTC));
	}

	/** Un aviso por correo a un contacto del paciente se acepta, queda pendiente de envío y la alerta avanza. */
	@Test
	void unAvisoValidoSeAceptaYQuedaPendiente() {
		RespuestaAviso respuesta = solicitar.solicitar(RELOJ, EVENT_ALERTA, pedido(HIJA, Canal.CORREO, 1));

		assertThat(respuesta.resultado()).isEqualTo(ResultadoAviso.ACEPTADO);
		assertThat(respuesta.motivo()).isNull();
		assertThat(respuesta.idIntento()).isNotNull();
		assertThat(respuesta.estadoAlerta().estado()).isEqualTo(EstadoContacto.AVISANDO_CONTACTO);
		assertThat(intentos.encolados).hasSize(1);
		assertThat(intentos.encolados.getFirst().pendienteDesde()).isEqualTo(AHORA);
	}

	/** WhatsApp con opt-in vigente se acepta igual que el correo. */
	@Test
	void whatsappConOptInSeAcepta() {
		RespuestaAviso respuesta = solicitar.solicitar(RELOJ, EVENT_ALERTA, pedido(HIJA, Canal.WHATSAPP, 1));

		assertThat(respuesta.resultado()).isEqualTo(ResultadoAviso.ACEPTADO);
		assertThat(intentos.encolados.getFirst().canal()).isEqualTo(Canal.WHATSAPP);
	}

	/** Repetir el mismo eventId devuelve el mismo aviso, sin encolar otro. */
	@Test
	void elMismoEventIdNoEnviaDosVeces() {
		SolicitudAviso pedido = pedido(HIJA, Canal.CORREO, 1);
		RespuestaAviso primera = solicitar.solicitar(RELOJ, EVENT_ALERTA, pedido);
		RespuestaAviso segunda = solicitar.solicitar(RELOJ, EVENT_ALERTA, pedido);

		assertThat(segunda.resultado()).isEqualTo(ResultadoAviso.ACEPTADO);
		assertThat(segunda.idIntento()).isEqualTo(primera.idIntento());
		assertThat(intentos.encolados).hasSize(1);
	}

	/** El mismo contacto, canal y ronda con otro eventId (reloj reiniciado) tampoco se envía dos veces. */
	@Test
	void elMismoDestinoConOtroEventIdNoEnviaDosVeces() {
		RespuestaAviso primera = solicitar.solicitar(RELOJ, EVENT_ALERTA, pedido(HIJA, Canal.CORREO, 1));
		RespuestaAviso otraVez = solicitar.solicitar(RELOJ, EVENT_ALERTA, pedido(HIJA, Canal.CORREO, 1));

		assertThat(otraVez.idIntento()).isEqualTo(primera.idIntento());
		assertThat(intentos.encolados).hasSize(1);

		// Otra ronda u otro canal sí son avisos distintos
		solicitar.solicitar(RELOJ, EVENT_ALERTA, pedido(HIJA, Canal.CORREO, 2));
		solicitar.solicitar(RELOJ, EVENT_ALERTA, pedido(HIJA, Canal.WHATSAPP, 1));
		assertThat(intentos.encolados).hasSize(3);
	}

	/** Un eventId ya usado para un aviso de otra alerta no es un reintento: 409. */
	@Test
	void unEventIdDeOtraAlertaResponde409() {
		UUID otraAlerta = UUID.randomUUID();
		UUID eventOtra = UUID.randomUUID();
		alertas.guardar(new AlertaGuardada(otraAlerta, ASIGNACION, 143, TipoEvento.FC, Severidad.MEDIA, false, false),
				eventOtra, EstadoContacto.RECIBIDA);
		SolicitudAviso pedido = pedido(HIJA, Canal.CORREO, 1);
		solicitar.solicitar(RELOJ, eventOtra, pedido);

		assertThatThrownBy(() -> solicitar.solicitar(RELOJ, EVENT_ALERTA, pedido)).isInstanceOf(Conflicto.class);
	}

	/** Cada motivo de rechazo del contrato, sin encolar nada. */
	@Test
	void losRechazosTraenSuMotivoYNoEncolanNada() {
		assertThat(rechazo(pedido(UUID.randomUUID(), Canal.CORREO, 1))).isEqualTo(MotivoRechazo.CONTACTO_DESCONOCIDO);
		assertThat(rechazo(pedido(SOBRINO, Canal.CORREO, 1))).isEqualTo(MotivoRechazo.CONTACTO_INACTIVO);
		assertThat(rechazo(pedido(VECINO, Canal.WHATSAPP, 1))).isEqualTo(MotivoRechazo.SIN_OPT_IN);
		assertThat(rechazo(pedido(AMIGA, Canal.WHATSAPP, 1))).isEqualTo(MotivoRechazo.SIN_OPT_IN);
		assertThat(rechazo(pedido(VECINO, Canal.CORREO, 1))).isEqualTo(MotivoRechazo.CANAL_NO_DISPONIBLE);
		assertThat(intentos.encolados).isEmpty();
	}

	/** Un contacto de otro paciente es desconocido para este reloj. */
	@Test
	void unContactoDeOtroPacienteEsDesconocido() {
		RelojAutenticado otroReloj = new RelojAutenticado(ASIGNACION, UUID.randomUUID(), UUID.randomUUID(), AHORA);

		RespuestaAviso respuesta = solicitar.solicitar(otroReloj, EVENT_ALERTA, pedido(HIJA, Canal.CORREO, 1));

		assertThat(respuesta.motivo()).isEqualTo(MotivoRechazo.CONTACTO_DESCONOCIDO);
	}

	/** Con la alerta confirmada, resuelta o cancelada, RECHAZADO con ALERTA_TERMINADA y detener el escalamiento. */
	@Test
	void conLaAlertaTerminadaSeRechazaYSePideDetenerse() {
		for (EstadoContacto terminado : List.of(EstadoContacto.CONFIRMADA, EstadoContacto.RESUELTA,
				EstadoContacto.CANCELADA)) {
			alertas.estados.put(ID_ALERTA, terminado);

			RespuestaAviso respuesta = solicitar.solicitar(RELOJ, EVENT_ALERTA, pedido(HIJA, Canal.CORREO, 1));

			assertThat(respuesta.motivo()).as(terminado.name()).isEqualTo(MotivoRechazo.ALERTA_TERMINADA);
			assertThat(respuesta.estadoAlerta().detenerEscalamiento()).isTrue();
		}
		assertThat(intentos.encolados).isEmpty();
	}

	/** Sin noticias del reloj (SIN_RESPUESTA) todavía se puede avisar: no está terminada. */
	@Test
	void sinRespuestaTodaviaSePuedeAvisar() {
		alertas.estados.put(ID_ALERTA, EstadoContacto.SIN_RESPUESTA);

		assertThat(solicitar.solicitar(RELOJ, EVENT_ALERTA, pedido(HIJA, Canal.CORREO, 1)).resultado())
				.isEqualTo(ResultadoAviso.ACEPTADO);
	}

	/** Una alerta que no es de este reloj responde 404, y un pedido incompleto o de otra ronda, 400. */
	@Test
	void alertaAjenaResponde404YPedidoMalFormado400() {
		RelojAutenticado otroVinculo = new RelojAutenticado(UUID.randomUUID(), UUID.randomUUID(), PACIENTE, AHORA);
		assertThatThrownBy(() -> solicitar.solicitar(otroVinculo, EVENT_ALERTA, pedido(HIJA, Canal.CORREO, 1)))
				.isInstanceOf(NoEncontrado.class);
		assertThatThrownBy(() -> solicitar.solicitar(RELOJ, UUID.randomUUID(), pedido(HIJA, Canal.CORREO, 1)))
				.isInstanceOf(NoEncontrado.class);

		assertThatThrownBy(() -> solicitar.solicitar(RELOJ, EVENT_ALERTA, pedido(HIJA, Canal.CORREO, 3)))
				.isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> solicitar.solicitar(RELOJ, EVENT_ALERTA,
				new SolicitudAviso(UUID.randomUUID(), HIJA, null, 1))).isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> solicitar.solicitar(RELOJ, EVENT_ALERTA,
				new SolicitudAviso(null, HIJA, Canal.CORREO, 1))).isInstanceOf(SolicitudRechazada.class);
	}

	/** @return el motivo del rechazo de ese pedido */
	private MotivoRechazo rechazo(SolicitudAviso pedido) {
		RespuestaAviso respuesta = solicitar.solicitar(RELOJ, EVENT_ALERTA, pedido);
		assertThat(respuesta.resultado()).isEqualTo(ResultadoAviso.RECHAZADO);
		assertThat(respuesta.idIntento()).isNull();
		assertThat(respuesta.estadoAlerta()).isNotNull();
		return respuesta.motivo();
	}

	/** @return un pedido con un eventId nuevo */
	private static SolicitudAviso pedido(UUID contacto, Canal canal, int ronda) {
		return new SolicitudAviso(UUID.randomUUID(), contacto, canal, ronda);
	}

	/** @return un contacto de la lista, con los canales y opt-in indicados */
	private static ContactoDelPaciente contacto(UUID id, String whatsapp, String correo, Instant optIn,
			Instant revocacion, boolean activo) {
		Persona persona = new Persona(UUID.randomUUID(), "Persona", whatsapp, correo, optIn, revocacion);
		return new ContactoDelPaciente(id, persona, new Vinculo("FAMILIA", null, false, null), 1, activo, 0);
	}

	/** Alertas guardadas en memoria; el estado pasa a AVISANDO_CONTACTO al encolar el primer aviso. */
	private final class AlertasEnMemoria implements AlertasDelReloj {

		private final Map<UUID, AlertaGuardada> porEventId = new HashMap<>();
		private final Map<UUID, EstadoContacto> estados = new HashMap<>();
		private final Map<UUID, UUID> eventIds = new HashMap<>();

		void guardar(AlertaGuardada alerta, UUID eventId, EstadoContacto estado) {
			porEventId.put(eventId, alerta);
			estados.put(alerta.idAlerta(), estado);
			eventIds.put(alerta.idAlerta(), eventId);
		}

		@Override
		public Optional<AlertaGuardada> buscar(UUID eventId) {
			return Optional.ofNullable(porEventId.get(eventId));
		}

		@Override
		public EstadoAlerta estado(UUID idAlerta) {
			EstadoContacto estado = estados.get(idAlerta);
			boolean conAvisos = intentos.encolados.stream().anyMatch(i -> i.idAlerta().equals(idAlerta));
			if (estado == EstadoContacto.RECIBIDA && conAvisos) {
				estado = EstadoContacto.AVISANDO_CONTACTO;
			}
			return EstadoAlerta.de(eventIds.get(idAlerta), estado, Severidad.GRAVE, null);
		}

	}

	/** Un aviso encolado en memoria. */
	private record Encolado(UUID idIntento, UUID eventId, UUID idAlerta, UUID idContacto, Canal canal, int ronda,
			Instant pendienteDesde) {
	}

	/** Bandeja de avisos en memoria, con las mismas claves únicas que registro_intento. */
	private static final class IntentosEnMemoria implements IntentosAviso {

		private final List<Encolado> encolados = new ArrayList<>();

		@Override
		public Optional<IntentoExistente> porEventId(UUID eventId) {
			return encolados.stream().filter(e -> e.eventId().equals(eventId))
					.map(e -> new IntentoExistente(e.idIntento(), e.idAlerta())).findFirst();
		}

		@Override
		public Optional<UUID> mismoDestino(UUID idAlerta, UUID idContacto, Canal canal, int ronda) {
			return encolados.stream()
					.filter(e -> e.idAlerta().equals(idAlerta) && e.idContacto().equals(idContacto)
							&& e.canal() == canal && e.ronda() == ronda)
					.map(Encolado::idIntento).findFirst();
		}

		@Override
		public Optional<UUID> encolar(UUID eventId, UUID idAlerta, UUID idContacto, Canal canal, int ronda,
				Instant ahora) {
			if (porEventId(eventId).isPresent() || mismoDestino(idAlerta, idContacto, canal, ronda).isPresent()) {
				return Optional.empty();
			}
			UUID id = UUID.randomUUID();
			encolados.add(new Encolado(id, eventId, idAlerta, idContacto, canal, ronda, ahora));
			return Optional.of(id);
		}

	}

}
