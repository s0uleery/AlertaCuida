package cl.ucn.alertacuida.application.sesion;

import java.time.Duration;
import java.time.Instant;

/**
 * Bloqueo por intentos fallidos: la cuenta queda bloqueada si tiene {@code intentos}
 * LOGIN_FALLIDO en la última {@code ventana} sin un LOGIN posterior. Los valores van en la configuración.
 */
public record PoliticaBloqueo(int intentos, Duration ventana) {

	public PoliticaBloqueo {
		if (intentos < 1 || ventana.isNegative() || ventana.isZero()) {
			throw new IllegalArgumentException("La política de bloqueo necesita intentos y una ventana positivos");
		}
	}

	/**
	 * @param ahora hora del intento
	 * @return desde cuándo cuentan los fallos
	 */
	public Instant inicioVentana(Instant ahora) {
		return ahora.minus(ventana);
	}

	/**
	 * @param fallidosRecientes intentos fallidos dentro de la ventana
	 * @return si la cuenta queda bloqueada
	 */
	public boolean bloquea(long fallidosRecientes) {
		return fallidosRecientes >= intentos;
	}

}
