package cl.ucn.alertacuida.application.aviso;

import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.application.aviso.modelo.BotonInteractivo;
import cl.ucn.alertacuida.application.aviso.modelo.ConversacionWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.EtapaConversacion;
import cl.ucn.alertacuida.application.aviso.modelo.EventoWhatsapp;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de la máquina de estados conversacional de WhatsApp Business (Flujo v3, 26-11-2026):
 * Nivel 1 (PRLAC), Nivel 2 (Tercero significativo), Nivel 3 (Servicios públicos), Simulacro, carreras por
 * alerta ya tomada, escalamiento por no disponibilidad y deduplicación de {@code messages[].id}.
 */
class GestionarConversacionWhatsappTest {

	private static final Instant T0 = Instant.parse("2026-11-26T15:00:00Z");

	private final ConversacionesEnMemoria repo = new ConversacionesEnMemoria();
	private final GestionarConversacionWhatsapp gestionar = new GestionarConversacionWhatsapp(repo,
			PoliticaConversacionWhatsapp.POR_DEFECTO, Clock.fixed(T0, ZoneOffset.UTC));

	/** Nivel 1 (PRLAC): tomar llamada → necesita atención → personal asignado → aún no llega → llegó → apoyo. */
	@Test
	void nivel1CompletaElFlujoYDejaCierreParcial() {
		UUID conv = repo.agregar(NivelConversacion.NIVEL_1_PRLAC, Severidad.MEDIA, "wamid.INIT");

		assertThat(gestionar.procesarBoton(boton("wamid.IN1", "wamid.INIT", "Llamaré al paciente"))).isTrue();
		assertThat(repo.etapa(conv)).isEqualTo(EtapaConversacion.ESPERANDO_ESTADO_LLAMADA);
		assertThat(repo.vencimiento(conv)).isEqualTo(T0.plus(Duration.ofMinutes(10)));
		assertThat(repo.avisosCambioEstado).hasSize(1);
		assertThat(repo.salientes.getLast().botones()).extracting(BotonInteractivo::id)
				.containsExactly("LLAMADA_BIEN", "LLAMADA_NECESITA_ATENCION", "LLAMADA_NO_CONTESTA");

		assertThat(gestionar.procesarBoton(boton("wamid.IN2", "wamid.INIT", "LLAMADA_NECESITA_ATENCION"))).isTrue();
		assertThat(repo.etapa(conv)).isEqualTo(EtapaConversacion.ESPERANDO_DISPONIBILIDAD);
		assertThat(repo.salientes.getLast().botones()).extracting(BotonInteractivo::id)
				.containsExactly("PERSONAL_ASIGNADO", "SIN_DISPONIBILIDAD");

		assertThat(gestionar.procesarBoton(boton("wamid.IN3", "wamid.INIT", "PERSONAL_ASIGNADO"))).isTrue();
		assertThat(repo.etapa(conv)).isEqualTo(EtapaConversacion.ESPERANDO_LLEGADA);
		Instant primerRecordatorio = repo.vencimiento(conv);
		assertThat(primerRecordatorio).isEqualTo(T0.plus(Duration.ofMinutes(15)));

		// "Aún no llega" no reinicia el vencimiento independiente
		assertThat(gestionar.procesarBoton(boton("wamid.IN4", "wamid.INIT", "AUN_NO_LLEGA"))).isTrue();
		assertThat(repo.etapa(conv)).isEqualTo(EtapaConversacion.ESPERANDO_LLEGADA);
		assertThat(repo.vencimiento(conv)).isEqualTo(primerRecordatorio);

		assertThat(gestionar.procesarBoton(boton("wamid.IN5", "wamid.INIT", "LLEGO"))).isTrue();
		assertThat(repo.etapa(conv)).isEqualTo(EtapaConversacion.ESPERANDO_RESULTADO_VISITA);
		assertThat(repo.llegadas).hasSize(1);

		assertThat(gestionar.procesarBoton(boton("wamid.IN6", "wamid.INIT", "RESULTADO_APOYO_NO_URGENTE"))).isTrue();
		assertThat(repo.etapa(conv)).isEqualTo(EtapaConversacion.COMPLETADA);
		assertThat(repo.resultadoCierre).isEqualTo(ResultadoCierreWhatsapp.APOYO_NO_URGENTE);
	}

	/** Verifica el resultado exacto de cierre parcial en Nivel 1 (APOYO_NO_URGENTE) y Nivel 2 (FALSA_ALARMA). */
	@Test
	void nivel2CierraComoFalsaAlarmaSiLaPersonaEstaBienEnLaLlamada() {
		UUID conv = repo.agregar(NivelConversacion.NIVEL_2_TERCERO, Severidad.LEVE, "wamid.N2");

		gestionar.procesarBoton(boton("wamid.M1", "wamid.N2", "TOMAR_LLAMADA"));
		gestionar.procesarBoton(boton("wamid.M2", "wamid.N2", "LLAMADA_BIEN"));

		assertThat(repo.etapa(conv)).isEqualTo(EtapaConversacion.COMPLETADA);
		assertThat(repo.resultadoCierre).isEqualTo(ResultadoCierreWhatsapp.FALSA_ALARMA);
		assertThat(repo.avisosCambioEstado).hasSize(2);
		assertThat(repo.salientes.getLast().cuerpo()).contains("cierre parcial");
	}

	/** Nivel 2 sin disponibilidad tras la llamada libera la alerta y notifica CAMBIO_ESTADO_ALERTA al reloj. */
	@Test
	void nivel2SinDisponibilidadLiberaLaAlertaYAvisaAlReloj() {
		UUID conv = repo.agregar(NivelConversacion.NIVEL_2_TERCERO, Severidad.GRAVE, "wamid.N2B");

		gestionar.procesarBoton(boton("wamid.B1", "wamid.N2B", "TOMAR_LLAMADA"));
		gestionar.procesarBoton(boton("wamid.B2", "wamid.N2B", "LLAMADA_NO_CONTESTA"));
		gestionar.procesarBoton(boton("wamid.B3", "wamid.N2B", "NO_PUEDO_ACUDIR"));

		assertThat(repo.etapa(conv)).isEqualTo(EtapaConversacion.RECHAZADA);
		assertThat(repo.resultadoCierre).isNull();
		assertThat(repo.avisosCambioEstado).hasSize(2);
		assertThat(repo.salientes.getLast().cuerpo()).contains("Escalaremos");
	}

	/** Nivel 3 y Simulacro se saltan la llamada previa y van directo a confirmación de llegada y cierre. */
	@Test
	void nivel3YSimulacroSaltanLaLlamadaYCompletanFlujoCompleto() {
		UUID sim = repo.agregar(NivelConversacion.SIMULACRO, Severidad.GRAVE, "wamid.SIM");

		gestionar.procesarBoton(boton("wamid.S1", "wamid.SIM", "Atender simulacro"));
		assertThat(repo.etapa(sim)).isEqualTo(EtapaConversacion.ESPERANDO_LLEGADA);
		assertThat(repo.vencimiento(sim)).isEqualTo(T0.plus(Duration.ofMinutes(5)));

		gestionar.procesarBoton(boton("wamid.S2", "wamid.SIM", "LLEGO"));
		assertThat(repo.etapa(sim)).isEqualTo(EtapaConversacion.ESPERANDO_RESULTADO_VISITA);

		gestionar.procesarBoton(boton("wamid.S3", "wamid.SIM", "RESULTADO_EMERGENCIA"));
		assertThat(repo.etapa(sim)).isEqualTo(EtapaConversacion.COMPLETADA);
		assertThat(repo.resultadoCierre).isEqualTo(ResultadoCierreWhatsapp.EMERGENCIA);
		assertThat(repo.salientes.getLast().cuerpo()).contains("SAMU");
	}

	/** Si otro contacto ya tomó la alerta, se le avisa al segundo por mensaje de sesión sin usar plantilla. */
	@Test
	void siOtroContactoYaTomoLaAlertaSeAvisaPorSesion() {
		UUID segunda = repo.agregarEnEstado(NivelConversacion.NIVEL_2_TERCERO, Severidad.MEDIA, "wamid.C2",
				EstadoContacto.CONFIRMADA);

		gestionar.procesarBoton(boton("wamid.R1", "wamid.C2", "TOMAR_LLAMADA"));

		assertThat(repo.etapa(segunda)).isEqualTo(EtapaConversacion.DESCARTADA);
		assertThat(repo.avisosCambioEstado).isEmpty();
		assertThat(repo.salientes.getLast().botones()).isEmpty();
		assertThat(repo.salientes.getLast().cuerpo()).contains("ya fue tomada");
	}

	/** Meta puede entregar el mismo messages[].id dos veces: el duplicado se ignora sin enviar otra respuesta. */
	@Test
	void unMensajeEntranteDuplicadoSeIgnora() {
		repo.agregar(NivelConversacion.NIVEL_3_SERVICIO, Severidad.GRAVE, "wamid.N3");

		assertThat(gestionar.procesarBoton(boton("wamid.DUP", "wamid.N3", "ATENDEREMOS"))).isTrue();
		assertThat(gestionar.procesarBoton(boton("wamid.DUP", "wamid.N3", "ATENDEREMOS"))).isTrue();

		assertThat(repo.salientes).hasSize(1);
		assertThat(repo.avisosCambioEstado).hasSize(1);
	}

	/** @return un evento de botón con id de mensaje entrante */
	private static EventoWhatsapp.Boton boton(String idEntrante, String wamidOriginal, String valor) {
		return new EventoWhatsapp.Boton(idEntrante, "56911111111", wamidOriginal, valor, T0);
	}

	/** Mensaje saliente encolado para aserciones en pruebas unitarias. */
	private record Saliente(UUID idConversacion, String cuerpo, List<BotonInteractivo> botones) {
	}

	/** Implementación en memoria de {@link ConversacionesWhatsapp}. */
	static final class ConversacionesEnMemoria implements ConversacionesWhatsapp {

		final Map<UUID, ConversacionWhatsapp> porId = new HashMap<>();
		final Map<String, UUID> porWamid = new HashMap<>();
		final Set<String> procesados = new HashSet<>();
		final List<Saliente> salientes = new ArrayList<>();
		final List<UUID> avisosCambioEstado = new ArrayList<>();
		final List<UUID> llegadas = new ArrayList<>();
		ResultadoCierreWhatsapp resultadoCierre;

		UUID agregar(NivelConversacion nivel, Severidad severidad, String wamid) {
			return agregarEnEstado(nivel, severidad, wamid, EstadoContacto.AVISANDO_CONTACTO);
		}

		UUID agregarEnEstado(NivelConversacion nivel, Severidad severidad, String wamid, EstadoContacto estado) {
			UUID id = UUID.randomUUID();
			ConversacionWhatsapp conv = new ConversacionWhatsapp(id, UUID.randomUUID(), UUID.randomUUID(), 142L,
					"Rosa", "+56987654321", severidad, estado, true, nivel, EtapaConversacion.ESPERANDO_ACEPTACION,
					wamid, 0, null, null);
			porId.put(id, conv);
			porWamid.put(wamid, id);
			return id;
		}

		EtapaConversacion etapa(UUID id) {
			return porId.get(id).etapa();
		}

		Instant vencimiento(UUID id) {
			return porId.get(id).proximoVencimiento();
		}

		@Override
		public void crear(UUID idIntento, UUID idAlerta, NivelConversacion nivel, String wamid, Instant ahora) {
		}

		@Override
		public boolean registrarMensajeProcesado(String idMensajeMeta, Instant ahora) {
			return procesados.add(idMensajeMeta);
		}

		@Override
		public Optional<ConversacionWhatsapp> buscarPorWamid(String wamid) {
			UUID id = porWamid.get(wamid);
			return id == null ? Optional.empty() : Optional.ofNullable(porId.get(id));
		}

		@Override
		public boolean tomar(UUID idConversacion, EtapaConversacion nuevaEtapa, Instant proximoVencimiento,
				Instant ventana24h, Instant ahora) {
			ConversacionWhatsapp actual = porId.get(idConversacion);
			if (actual == null || actual.etapa() != EtapaConversacion.ESPERANDO_ACEPTACION) {
				return false;
			}
			porId.put(idConversacion, con(actual, nuevaEtapa, proximoVencimiento, ventana24h));
			return true;
		}

		@Override
		public boolean avanzar(UUID idConversacion, EtapaConversacion etapaEsperada, EtapaConversacion nuevaEtapa,
				Instant proximoVencimiento, Instant ventana24h, Instant ahora) {
			ConversacionWhatsapp actual = porId.get(idConversacion);
			if (actual == null || actual.etapa() != etapaEsperada) {
				return false;
			}
			porId.put(idConversacion, con(actual, nuevaEtapa, proximoVencimiento, ventana24h));
			return true;
		}

		@Override
		public void refrescarVentana(UUID idConversacion, Instant ventana24h, Instant ahora) {
			ConversacionWhatsapp actual = porId.get(idConversacion);
			porId.put(idConversacion, con(actual, actual.etapa(), actual.proximoVencimiento(), ventana24h));
		}

		@Override
		public void registrarRecordatorio(UUID idConversacion, int recordatoriosEnviados, Instant proximoVencimiento,
				Instant ahora) {
		}

		@Override
		public void cerrarConversacion(UUID idConversacion, EtapaConversacion etapaFinal, Instant ahora) {
			ConversacionWhatsapp actual = porId.get(idConversacion);
			porId.put(idConversacion, con(actual, etapaFinal, null, actual.ventana24hHasta()));
		}

		@Override
		public void registrarRespuestaIntento(UUID idIntento, RespuestaContacto respuesta, Instant ahora) {
		}

		@Override
		public void registrarLlegadaIntento(UUID idIntento, Instant ahora) {
			llegadas.add(idIntento);
		}

		@Override
		public boolean cerrarAlertaParcial(UUID idAlerta, ResultadoCierreWhatsapp resultado, Instant ahora) {
			this.resultadoCierre = resultado;
			return true;
		}

		@Override
		public void dejarAvisoCambioEstadoReloj(UUID idAlerta) {
			avisosCambioEstado.add(idAlerta);
		}

		@Override
		public void encolarMensajeSaliente(UUID idConversacion, String cuerpo, List<BotonInteractivo> botones,
				Instant ahora) {
			salientes.add(new Saliente(idConversacion, cuerpo, botones));
		}

		@Override
		public List<ConversacionWhatsapp> vencidas(Instant ahora) {
			return List.of();
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

		private static ConversacionWhatsapp con(ConversacionWhatsapp c, EtapaConversacion etapa,
				Instant vencimiento, Instant ventana) {
			return new ConversacionWhatsapp(c.idConversacion(), c.idIntento(), c.idAlerta(), c.numeroAlerta(),
					c.aliasPaciente(), c.telefonoReloj(), c.severidad(), c.estadoAlerta(), c.delReloj(), c.nivel(),
					etapa, c.ultimoWamid(), c.recordatoriosEnviados(), vencimiento, ventana);
		}

	}

}
