package cl.ucn.alertacuida.application.aviso.modelo;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Cuánto esperar antes de cada reintento de un envío fallido. Agotados los reintentos, el envío
 * queda fallido.
 *
 * @param esperas la espera antes del primer reintento, del segundo, etc.
 */
public record PoliticaReintentos(List<Duration> esperas) {

	/** Copia la lista para que no cambie desde afuera. */
	public PoliticaReintentos {
		esperas = List.copyOf(esperas);
	}

	/**
	 * @param reintentosHechos cuántos reintentos ya se hicieron
	 * @return la espera hasta el siguiente reintento, o vacío si ya no quedan
	 */
	public Optional<Duration> esperaTras(int reintentosHechos) {
		return reintentosHechos < esperas.size() ? Optional.of(esperas.get(reintentosHechos)) : Optional.empty();
	}

}
