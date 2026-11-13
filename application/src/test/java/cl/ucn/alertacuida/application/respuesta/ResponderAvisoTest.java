package cl.ucn.alertacuida.application.respuesta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.respuesta.modelo.AvisoRespondible;
import cl.ucn.alertacuida.application.respuesta.modelo.RespuestaContacto;
import cl.ucn.alertacuida.application.respuesta.modelo.VistaRespuesta;
import cl.ucn.alertacuida.application.respuesta.puerto.FirmaRespuesta;
import cl.ucn.alertacuida.application.respuesta.puerto.RespuestasContacto;
import cl.ucn.alertacuida.domain.alerta.EstadoContacto;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
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
import org.junit.jupiter.api.Test;

/**
 * Respuesta de un contacto del paciente desde el enlace de su correo ("Confirmo que voy" o "No puedo
 * asistir"). Cada caso sale de una decisión del 12-11-2026, no del código:
 * <ul>
 * <li>R1 El enlace identifica el aviso con un token firmado por el servidor; uno alterado, inventado o de
 * un aviso que no es un correo a un contacto del paciente no sirve, con el mismo mensaje para todo.</li>
 * <li>R2 Abrir el enlace no registra nada: muestra la alerta (alias o folio, tipo, gravedad y hora) y
 * espera que la persona presione un botón.</li>
 * <li>R3 "Voy" cuenta igual que el botón de WhatsApp: confirma la alerta y avisa al reloj para que deje de
 * escalar. "No puedo" queda registrado, sin aviso al reloj.</li>
 * <li>R4 Vale una sola respuesta: la primera; después solo se muestra lo que respondió.</li>
 * <li>R5 Si la alerta fue resuelta o cancelada, no se registra y la página lo dice. Si otro contacto ya
 * confirmó o el reloj terminó la lista sin respuesta, se registra igual.</li>
 * </ul>
 */
class ResponderAvisoTest {

	private static final Instant AHORA = Instant.parse("2026-11-12T15:00:00Z");
	private static final Instant DETECCION = AHORA.minusSeconds(300);

	private final RespuestasEnMemoria respuestas = new RespuestasEnMemoria();
	private final ResponderAviso responder = new ResponderAviso(new FirmaDePrueba(), respuestas,
			enTransaccion(), Clock.fixed(AHORA, ZoneOffset.UTC));

	/** R1: un token sin firma válida se rechaza. */
	@Test
	void tokenAlteradoNoSirve() {
		assertThatThrownBy(() -> responder.consultar("inventado"))
				.isInstanceOf(SolicitudRechazada.class)
				.hasMessageContaining("enlace");
		assertThatThrownBy(() -> responder.responder("inventado", RespuestaContacto.VOY_EN_CAMINO))
				.isInstanceOf(SolicitudRechazada.class);
	}

	/** R1: un token bien firmado de un aviso que no es un correo a un contacto del paciente no sirve. */
	@Test
	void avisoQueNoEsDeUnContactoNoSirve() {
		String token = FirmaDePrueba.token(UUID.randomUUID());

		assertThatThrownBy(() -> responder.consultar(token))
				.isInstanceOf(SolicitudRechazada.class)
				.hasMessageContaining("enlace");
	}

	/** R2: la consulta muestra la alerta y no registra nada. */
	@Test
	void consultarMuestraLaAlertaSinRegistrar() {
		UUID intento = respuestas.agregar(EstadoContacto.AVISANDO_CONTACTO);

		VistaRespuesta vista = responder.consultar(FirmaDePrueba.token(intento));

		assertThat(vista.numero()).isEqualTo(142);
		assertThat(vista.alias()).isEqualTo("Rosa");
		assertThat(vista.tipo()).isEqualTo(TipoEvento.CAIDA);
		assertThat(vista.severidad()).isEqualTo(Severidad.GRAVE);
		assertThat(vista.fechaHora()).isEqualTo(DETECCION);
		assertThat(vista.respuesta()).isNull();
		assertThat(vista.cerrada()).isFalse();
		assertThat(respuestas.respuesta(intento)).isNull();
	}

	/** R3: "voy" queda registrado con la hora y avisa al reloj que la alerta está confirmada. */
	@Test
	void voyConfirmaYAvisaAlReloj() {
		UUID intento = respuestas.agregar(EstadoContacto.AVISANDO_CONTACTO);

		VistaRespuesta vista = responder.responder(FirmaDePrueba.token(intento), RespuestaContacto.VOY_EN_CAMINO);

		assertThat(vista.respuesta()).isEqualTo(RespuestaContacto.VOY_EN_CAMINO);
		assertThat(vista.fechaRespuesta()).isEqualTo(AHORA);
		assertThat(respuestas.respuesta(intento)).isEqualTo(RespuestaContacto.VOY_EN_CAMINO);
		assertThat(respuestas.confirmadasAlReloj).containsExactly(RespuestasEnMemoria.ALERTA);
	}

	/** R3: "no puedo" queda registrado y no avisa al reloj, que sigue con el siguiente contacto. */
	@Test
	void noPuedoQuedaRegistradoSinAvisarAlReloj() {
		UUID intento = respuestas.agregar(EstadoContacto.AVISANDO_CONTACTO);

		VistaRespuesta vista = responder.responder(FirmaDePrueba.token(intento), RespuestaContacto.NO_PUEDO);

		assertThat(vista.respuesta()).isEqualTo(RespuestaContacto.NO_PUEDO);
		assertThat(respuestas.respuesta(intento)).isEqualTo(RespuestaContacto.NO_PUEDO);
		assertThat(respuestas.confirmadasAlReloj).isEmpty();
	}

	/** R4: la segunda respuesta no cambia la primera, y se devuelve la primera. */
	@Test
	void valeSoloLaPrimeraRespuesta() {
		UUID intento = respuestas.agregar(EstadoContacto.AVISANDO_CONTACTO);
		String token = FirmaDePrueba.token(intento);
		responder.responder(token, RespuestaContacto.NO_PUEDO);

		VistaRespuesta vista = responder.responder(token, RespuestaContacto.VOY_EN_CAMINO);

		assertThat(vista.respuesta()).isEqualTo(RespuestaContacto.NO_PUEDO);
		assertThat(respuestas.respuesta(intento)).isEqualTo(RespuestaContacto.NO_PUEDO);
		assertThat(respuestas.confirmadasAlReloj).isEmpty();
	}

	/** R5: con la alerta resuelta o cancelada no se registra, y la vista lo indica. */
	@Test
	void alertaResueltaOCanceladaNoRegistra() {
		for (EstadoContacto estado : List.of(EstadoContacto.RESUELTA, EstadoContacto.CANCELADA)) {
			UUID intento = respuestas.agregar(estado);

			VistaRespuesta vista = responder.responder(FirmaDePrueba.token(intento),
					RespuestaContacto.VOY_EN_CAMINO);

			assertThat(vista.cerrada()).as(estado.name()).isTrue();
			assertThat(vista.respuesta()).as(estado.name()).isNull();
			assertThat(respuestas.respuesta(intento)).as(estado.name()).isNull();
		}
		assertThat(respuestas.confirmadasAlReloj).isEmpty();
	}

	/** R5: si otro ya confirmó o el reloj terminó la lista, la respuesta se registra igual. */
	@Test
	void confirmadaPorOtroOSinRespuestaRegistraIgual() {
		for (EstadoContacto estado : List.of(EstadoContacto.CONFIRMADA, EstadoContacto.SIN_RESPUESTA)) {
			UUID intento = respuestas.agregar(estado);

			VistaRespuesta vista = responder.responder(FirmaDePrueba.token(intento),
					RespuestaContacto.VOY_EN_CAMINO);

			assertThat(vista.cerrada()).as(estado.name()).isFalse();
			assertThat(respuestas.respuesta(intento)).as(estado.name()).isEqualTo(RespuestaContacto.VOY_EN_CAMINO);
		}
	}

	/** @return transacciones que solo ejecutan el trabajo */
	private static Transacciones enTransaccion() {
		return Supplier::get;
	}

	/** Firma de prueba: el token es "firmado:" seguido del id del aviso. */
	private static final class FirmaDePrueba implements FirmaRespuesta {

		private static final String PREFIJO = "firmado:";

		/** @return el token que esta firma acepta para ese aviso */
		static String token(UUID idIntento) {
			return PREFIJO + idIntento;
		}

		@Override
		public String firmar(UUID idIntento) {
			return token(idIntento);
		}

		@Override
		public Optional<UUID> verificar(String token) {
			return token.startsWith(PREFIJO) ? Optional.of(UUID.fromString(token.substring(PREFIJO.length())))
					: Optional.empty();
		}

	}

	/** Avisos por correo a contactos de una misma alerta (#142, caída grave de Rosa), en memoria. */
	private static final class RespuestasEnMemoria implements RespuestasContacto {

		static final UUID ALERTA = UUID.randomUUID();

		private final Map<UUID, AvisoRespondible> avisos = new HashMap<>();
		final List<UUID> confirmadasAlReloj = new ArrayList<>();

		/** @return un aviso nuevo, sin respuesta, de una alerta en ese estado */
		UUID agregar(EstadoContacto estado) {
			UUID id = UUID.randomUUID();
			avisos.put(id, new AvisoRespondible(id, ALERTA, 142, "Rosa", TipoEvento.CAIDA, Severidad.GRAVE,
					DETECCION, estado, null, null));
			return id;
		}

		/** @return lo que respondió ese aviso, o null */
		RespuestaContacto respuesta(UUID idIntento) {
			return avisos.get(idIntento).respuesta();
		}

		@Override
		public Optional<AvisoRespondible> buscar(UUID idIntento) {
			return Optional.ofNullable(avisos.get(idIntento));
		}

		@Override
		public boolean registrar(UUID idIntento, RespuestaContacto respuesta, Instant fecha) {
			AvisoRespondible aviso = avisos.get(idIntento);
			if (aviso.respuesta() != null) {
				return false;
			}
			avisos.put(idIntento, new AvisoRespondible(aviso.idIntento(), aviso.idAlerta(), aviso.numero(),
					aviso.alias(), aviso.tipo(), aviso.severidad(), aviso.fechaHora(), aviso.estado(), respuesta,
					fecha));
			return true;
		}

		@Override
		public void dejarAvisoConfirmadaPendiente(UUID idAlerta) {
			confirmadasAlReloj.add(idAlerta);
		}

	}

}
