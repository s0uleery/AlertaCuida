package cl.ucn.alertacuida.application.personal;

import cl.ucn.alertacuida.application.aviso.PlantillasCorreo;
import cl.ucn.alertacuida.application.aviso.puerto.CanalNotificacion;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.personal.modelo.CuentaRecuperable;
import cl.ucn.alertacuida.application.personal.puerto.CuentasRecuperacion;
import cl.ucn.alertacuida.application.personal.puerto.TokensRecuperacion;
import cl.ucn.alertacuida.domain.comun.Run;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Una persona del equipo olvidó su contraseña: escribe su RUN y, si su cuenta está activa y tiene correo, le
 * llega un enlace de un solo uso para elegir otra (plantilla "recuperacion" de correos.yml).
 *
 * <p>Quien pide no sabe si la cuenta existe: el caso de uso no devuelve nada ni lanza un error distinto, para no
 * revelar quién está registrado. Como máximo se envían {@code maximoPorHora} enlaces por cuenta en una hora, para
 * que nadie inunde el buzón de otra persona; pasado el tope, la solicitud se ignora sin avisar.
 */
public class SolicitarRecuperacion {

	private static final System.Logger LOG = System.getLogger(SolicitarRecuperacion.class.getName());
	private static final Duration VENTANA_TOPE = Duration.ofHours(1);
	/** Un enlace vencido se guarda un día más, para el tope y por si hay que revisar algo; después se borra. */
	private static final Duration CONSERVAR_VENCIDOS = Duration.ofDays(1);

	private final CuentasRecuperacion cuentas;
	private final TokensRecuperacion tokens;
	private final SecretosUnSoloUso secretos;
	private final HashRun hashRun;
	private final PlantillasCorreo plantillas;
	private final CanalNotificacion canal;
	private final Transacciones transacciones;
	private final Clock reloj;
	private final String urlRestablecer;
	private final Duration vigencia;
	private final int maximoPorHora;

	/**
	 * Recibe lo que usa; lo arma ConfiguracionCasosDeUso.
	 *
	 * @param canal          correo institucional o canal simulado
	 * @param urlRestablecer página del dashboard donde se elige la contraseña nueva; el token va después de "#"
	 * @param vigencia       cuánto dura un enlace sin usar (1 h en application.properties)
	 * @param maximoPorHora  enlaces que se envían como máximo a una cuenta en una hora
	 */
	public SolicitarRecuperacion(CuentasRecuperacion cuentas, TokensRecuperacion tokens, SecretosUnSoloUso secretos,
			HashRun hashRun, PlantillasCorreo plantillas, CanalNotificacion canal, Transacciones transacciones,
			Clock reloj, String urlRestablecer, Duration vigencia, int maximoPorHora) {
		if (vigencia.isNegative() || vigencia.isZero()) {
			throw new IllegalArgumentException("La vigencia del enlace debe ser positiva");
		}
		if (maximoPorHora < 1) {
			throw new IllegalArgumentException("El tope de enlaces por hora debe ser 1 o más");
		}
		this.cuentas = cuentas;
		this.tokens = tokens;
		this.secretos = secretos;
		this.hashRun = hashRun;
		this.plantillas = plantillas;
		this.canal = canal;
		this.transacciones = transacciones;
		this.reloj = reloj;
		this.urlRestablecer = urlRestablecer;
		this.vigencia = vigencia;
		this.maximoPorHora = maximoPorHora;
	}

	/**
	 * Envía el enlace si corresponde. Un error del correo no se propaga: la respuesta es siempre la misma.
	 *
	 * @param run el que escribió la persona, con o sin puntos
	 * @throws SolicitudRechazada solo si el RUN está mal escrito (no revela si existe)
	 */
	public void solicitar(String run) {
		Run normalizado;
		try {
			normalizado = Run.de(run);
		} catch (IllegalArgumentException malEscrito) {
			throw new SolicitudRechazada("El RUN no es válido");
		}
		Optional<CuentaRecuperable> cuenta = cuentas.buscarActivaPorRunHash(hashRun.de(normalizado))
				.filter(encontrada -> encontrada.correo() != null && !encontrada.correo().isBlank());
		if (cuenta.isEmpty()) {
			return;
		}
		Instant ahora = reloj.instant();
		Optional<String> token = transacciones.ejecutar(() -> emitir(cuenta.get(), ahora));
		token.ifPresent(emitido -> enviar(cuenta.get(), emitido, ahora.plus(vigencia)));
	}

	/** @return el token nuevo, o vacío si la cuenta ya llegó al tope de la última hora */
	private Optional<String> emitir(CuentaRecuperable cuenta, Instant ahora) {
		tokens.borrarVencidos(cuenta.id(), ahora.minus(CONSERVAR_VENCIDOS));
		if (tokens.emitidosDesde(cuenta.id(), ahora.minus(VENTANA_TOPE)) >= maximoPorHora) {
			return Optional.empty();
		}
		String token = secretos.nuevo();
		tokens.guardar(cuenta.id(), secretos.hash(token), ahora, ahora.plus(vigencia));
		return Optional.of(token);
	}

	/** Un correo que no sale queda en el log: la persona puede volver a pedirlo. */
	private void enviar(CuentaRecuperable cuenta, String token, Instant expiraEn) {
		try {
			canal.enviar(plantillas.recuperacion(cuenta.correo(), cuenta.nombre(), urlRestablecer + "#" + token,
					expiraEn));
		} catch (RuntimeException falla) {
			LOG.log(System.Logger.Level.WARNING, "No se pudo enviar el correo de recuperación de la cuenta "
					+ cuenta.id() + ": " + falla.getMessage());
		}
	}

}
