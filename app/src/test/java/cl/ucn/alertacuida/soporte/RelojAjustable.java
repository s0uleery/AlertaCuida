package cl.ucn.alertacuida.soporte;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Reloj de las pruebas que se puede fijar y adelantar. Reemplaza al reloj del sistema para recorrer un
 * escalamiento minuto a minuto sin esperar, siempre en el mismo día hábil.
 */
public class RelojAjustable extends Clock {

	private volatile Instant ahora;

	/** @param inicio la hora con que parte */
	public RelojAjustable(Instant inicio) {
		this.ahora = inicio;
	}

	/** Pone el reloj en esa hora. */
	public void fijar(Instant hora) {
		ahora = hora;
	}

	/** Adelanta el reloj en ese tiempo. */
	public void adelantar(Duration tiempo) {
		ahora = ahora.plus(tiempo);
	}

	@Override
	public Instant instant() {
		return ahora;
	}

	/** @return siempre UTC, como el reloj de la aplicación */
	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	/** @return este mismo reloj si la zona es UTC; la aplicación no pide otras */
	@Override
	public Clock withZone(ZoneId zona) {
		if (!ZoneOffset.UTC.equals(zona)) {
			throw new UnsupportedOperationException("El reloj de las pruebas solo trabaja en UTC");
		}
		return this;
	}

}
