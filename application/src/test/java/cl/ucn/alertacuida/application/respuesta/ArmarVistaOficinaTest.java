package cl.ucn.alertacuida.application.respuesta;

import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.application.respuesta.modelo.AvisoRespondible;
import cl.ucn.alertacuida.application.respuesta.modelo.ContactoAvisado;
import cl.ucn.alertacuida.application.respuesta.modelo.EstadoOficina;
import cl.ucn.alertacuida.application.respuesta.modelo.EtapaOficina;
import cl.ucn.alertacuida.application.respuesta.modelo.ResultadoLlamadaOficina;
import cl.ucn.alertacuida.application.respuesta.modelo.ResultadoVisita;
import cl.ucn.alertacuida.application.respuesta.modelo.VistaOficina;
import cl.ucn.alertacuida.application.respuesta.puerto.PasosOficina;
import cl.ucn.alertacuida.domain.alerta.EstadoContacto;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Etapa de los pasos de la oficina en el enlace del correo, igual al recorrido de los mensajes de WhatsApp N1-02 a
 * N1-11. Cada caso sale de las decisiones del 26-11-2026, no del código:
 * <ul>
 * <li>R1 Sin tomar se ofrece tomarla; tomada por otra cuenta, solo se ve quién la tiene.</li>
 * <li>R2 Tomada: llamar; bien → confirmar el evento; necesita atención o no contesta → coordinar la visita.</li>
 * <li>R3 Personal asignado → esperar la llegada (con recordatorio pasado el plazo); sin disponibilidad → el reloj
 * sigue avisando, y se muestra el último contacto avisado.</li>
 * <li>R4 Llegó → resultado de la visita; bien → confirmar el evento; emergencia → su desenlace.</li>
 * <li>R5 Cerrada o cancelada → cerrada, pase lo que pase con los pasos.</li>
 * <li>R6 El enlace de un contacto del paciente no tiene pasos de la oficina.</li>
 * </ul>
 */
class ArmarVistaOficinaTest {

	private static final Instant AHORA = Instant.parse("2026-11-26T15:00:00Z");
	private static final UUID YO = UUID.randomUUID();
	private static final UUID OTRA = UUID.randomUUID();

	/** R1: sin responsable, se ofrece tomarla. */
	@Test
	void sinResponsableSeOfreceTomarla() {
		assertThat(etapa(EstadoContacto.AVISANDO_CONTACTO, EstadoOficina.VACIO)).isEqualTo(EtapaOficina.SIN_TOMAR);
	}

	/** R1: si la tiene otra cuenta, no se avanza. */
	@Test
	void laTieneOtraCuenta() {
		assertThat(etapa(EstadoContacto.AVISANDO_CONTACTO, tomada(OTRA, null, null, null, null, null)))
				.isEqualTo(EtapaOficina.A_CARGO_DE_OTRO);
	}

	/** R2: tomada sin llamada, se llama; luego según el resultado. */
	@Test
	void despuesDeTomarSeLlama() {
		assertThat(etapa(EstadoContacto.AVISANDO_CONTACTO, tomada(YO, null, null, null, null, null)))
				.isEqualTo(EtapaOficina.LLAMAR);
		assertThat(etapa(EstadoContacto.CONFIRMADA, tomada(YO, ResultadoLlamadaOficina.BIEN, null, null, null, null)))
				.isEqualTo(EtapaOficina.CONFIRMAR_EVENTO);
		assertThat(etapa(EstadoContacto.AVISANDO_CONTACTO,
				tomada(YO, ResultadoLlamadaOficina.NO_CONTESTA, null, null, null, null)))
				.isEqualTo(EtapaOficina.COORDINAR_VISITA);
	}

	/** R3: con personal asignado se espera la llegada; sin disponibilidad, el reloj sigue. */
	@Test
	void coordinarLaVisita() {
		assertThat(etapa(EstadoContacto.CONFIRMADA,
				tomada(YO, ResultadoLlamadaOficina.NECESITA_ATENCION, AHORA, null, null, null)))
				.isEqualTo(EtapaOficina.ESPERANDO_LLEGADA);
		assertThat(etapa(EstadoContacto.AVISANDO_CONTACTO,
				tomada(YO, ResultadoLlamadaOficina.NECESITA_ATENCION, null, AHORA, null, null)))
				.isEqualTo(EtapaOficina.SIN_DISPONIBILIDAD);
	}

	/** R4: llegó, luego según cómo la encontró. */
	@Test
	void resultadoDeLaVisita() {
		assertThat(etapa(EstadoContacto.CONFIRMADA,
				tomada(YO, ResultadoLlamadaOficina.NO_CONTESTA, AHORA, null, AHORA, null)))
				.isEqualTo(EtapaOficina.RESULTADO_VISITA);
		assertThat(etapa(EstadoContacto.CONFIRMADA,
				tomada(YO, ResultadoLlamadaOficina.NO_CONTESTA, AHORA, null, AHORA, ResultadoVisita.BIEN)))
				.isEqualTo(EtapaOficina.CONFIRMAR_EVENTO);
		assertThat(etapa(EstadoContacto.CONFIRMADA,
				tomada(YO, ResultadoLlamadaOficina.NO_CONTESTA, AHORA, null, AHORA, ResultadoVisita.EMERGENCIA)))
				.isEqualTo(EtapaOficina.EMERGENCIA);
	}

	/** R5: resuelta o cancelada queda cerrada, aunque falten pasos o la tenga otra cuenta. */
	@Test
	void cerradaGanaATodo() {
		assertThat(etapa(EstadoContacto.RESUELTA, tomada(OTRA, null, null, null, null, null)))
				.isEqualTo(EtapaOficina.CERRADA);
		assertThat(etapa(EstadoContacto.CANCELADA, EstadoOficina.VACIO)).isEqualTo(EtapaOficina.CERRADA);
	}

	/** R3: el recordatorio aparece al cumplirse el plazo, no antes. */
	@Test
	void elRecordatorioApareceAlCumplirseElPlazo() {
		Duration plazo = Duration.ofMinutes(30);
		EstadoOficina reciente = tomada(YO, ResultadoLlamadaOficina.NO_CONTESTA, AHORA.minusSeconds(60), null, null,
				null);
		EstadoOficina antiguo = tomada(YO, ResultadoLlamadaOficina.NO_CONTESTA, AHORA.minus(plazo), null, null, null);

		assertThat(vista(EstadoContacto.CONFIRMADA, reciente, plazo).recordatorio()).isFalse();
		assertThat(vista(EstadoContacto.CONFIRMADA, antiguo, plazo).recordatorio()).isTrue();
	}

	/** R3: sin disponibilidad se muestra a quién avisó el reloj después. */
	@Test
	void sinDisponibilidadMuestraElUltimoContacto() {
		VistaOficina vista = vista(EstadoContacto.AVISANDO_CONTACTO,
				tomada(YO, ResultadoLlamadaOficina.NECESITA_ATENCION, null, AHORA, null, null), Duration.ofMinutes(30));

		assertThat(vista.ultimoContacto()).isEqualTo("Hija");
		assertThat(vista.esResponsable()).isTrue();
	}

	/** R6: el aviso a un contacto del paciente no trae pasos de la oficina. */
	@Test
	void elContactoNoTienePasosDeLaOficina() {
		ArmarVistaOficina armar = new ArmarVistaOficina(pasos(EstadoOficina.VACIO), Duration.ofMinutes(30),
				Clock.fixed(AHORA, ZoneOffset.UTC));

		assertThat(armar.de(aviso(EstadoContacto.AVISANDO_CONTACTO, null))).isNull();
	}

	/** @return la etapa del aviso de "YO" con ese estado de contacto y esos pasos */
	private static EtapaOficina etapa(EstadoContacto contacto, EstadoOficina estado) {
		return ArmarVistaOficina.etapa(aviso(contacto, YO), estado);
	}

	/** @return la vista del aviso de "YO", con esos pasos y ese plazo de recordatorio */
	private static VistaOficina vista(EstadoContacto contacto, EstadoOficina estado, Duration plazo) {
		return new ArmarVistaOficina(pasos(estado), plazo, Clock.fixed(AHORA, ZoneOffset.UTC))
				.de(aviso(contacto, YO));
	}

	/** @return lo registrado por la oficina, tomada por esa cuenta */
	private static EstadoOficina tomada(UUID responsable, ResultadoLlamadaOficina llamada, Instant personal,
			Instant sinDisponibilidad, Instant llegada, ResultadoVisita visita) {
		return new EstadoOficina(responsable, "Ana", llamada, personal, sinDisponibilidad, llegada, visita, null,
				null);
	}

	/** @return un aviso de la alerta #142 de Rosa; con coordinador, es de la oficina */
	private static AvisoRespondible aviso(EstadoContacto estado, UUID coordinador) {
		return new AvisoRespondible(UUID.randomUUID(), UUID.randomUUID(), 142, "Rosa", TipoEvento.CAIDA,
				Severidad.MEDIA, AHORA.minusSeconds(600), estado, null, null, null, "+56987654321", true, coordinador,
				null);
	}

	/** @return pasos en memoria que siempre devuelven ese estado, con la hija como último contacto avisado */
	private static PasosOficina pasos(EstadoOficina estado) {
		return new PasosOficina() {

			@Override
			public EstadoOficina buscar(UUID idAlerta) {
				return estado;
			}

			@Override
			public boolean registrarLlamada(UUID idAlerta, UUID idUsuario, ResultadoLlamadaOficina resultado,
					Instant fecha) {
				return false;
			}

			@Override
			public boolean asignarPersonal(UUID idAlerta, UUID idUsuario, Instant fecha) {
				return false;
			}

			@Override
			public boolean sinDisponibilidad(UUID idAlerta, UUID idUsuario, Instant fecha) {
				return false;
			}

			@Override
			public boolean registrarLlegada(UUID idAlerta, UUID idUsuario, Instant fecha) {
				return false;
			}

			@Override
			public boolean registrarVisita(UUID idAlerta, UUID idUsuario, ResultadoVisita resultado, Instant fecha) {
				return false;
			}

			@Override
			public boolean agregarComentario(UUID idAlerta, UUID idUsuario, String texto) {
				return false;
			}

			@Override
			public Optional<ContactoAvisado> ultimoContactoAvisado(UUID idAlerta) {
				return Optional.of(new ContactoAvisado("Hija", AHORA.minusSeconds(120)));
			}

		};
	}

}
