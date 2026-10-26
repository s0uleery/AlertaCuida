package cl.ucn.alertacuida.application.personal;

import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.application.personal.modelo.EnlaceActivacion;
import cl.ucn.alertacuida.application.personal.puerto.TokensActivacion;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Emite el enlace de activación de una cuenta y anula los anteriores: solo el último sirve. */
public class EmisorEnlacesActivacion {

	private final TokensActivacion tokens;
	private final SecretosUnSoloUso secretos;
	private final Duration vigencia;

	/** @param vigencia cuánto dura un enlace sin usar (48 h en application.properties) */
	public EmisorEnlacesActivacion(TokensActivacion tokens, SecretosUnSoloUso secretos, Duration vigencia) {
		if (vigencia.isNegative() || vigencia.isZero()) {
			throw new IllegalArgumentException("La vigencia del enlace debe ser positiva");
		}
		this.tokens = tokens;
		this.secretos = secretos;
		this.vigencia = vigencia;
	}

	/**
	 * Llamar dentro de una transacción: anula los enlaces anteriores y guarda el nuevo.
	 *
	 * @param idUsuario cuenta a la que pertenece el enlace
	 * @param ahora     hora de emisión; el enlace vence {@code vigencia} después
	 * @return el enlace con el token, que solo existe en esta respuesta
	 */
	EnlaceActivacion emitir(UUID idUsuario, Instant ahora) {
		tokens.anularPendientes(idUsuario);
		String token = secretos.nuevo();
		Instant expira = ahora.plus(vigencia);
		tokens.guardar(idUsuario, secretos.hash(token), ahora, expira);
		return new EnlaceActivacion(idUsuario, token, expira);
	}

}
