package cl.ucn.alertacuida.application.aviso;

import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.domain.calendario.CalendarioHabil;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Lo que comparten los avisos que el servidor decide enviar al equipo (el aviso a la oficina, los correos de
 * recibe_avisos y el vigilante del escalamiento): solo se avisa lo ocurrido dentro de la ventana y en horario
 * laboral, y cada aviso se encola en su propia transacción, de modo que si uno falla se registra y se sigue con
 * los demás. Cada caso de uso pone su propia regla de a quién y cuándo.
 */
public final class EncoladorAlEquipo {

	private final CalendarioHabil calendario;
	private final Duration ventana;
	private final Transacciones transacciones;
	private final System.Logger log;

	/**
	 * @param calendario horario laboral y feriados
	 * @param ventana    hasta qué tan atrás se avisa; lo más antiguo no (por ejemplo, tras una caída del servidor)
	 * @param log        registro del caso de uso que avisa, para que el error diga de dónde viene
	 */
	public EncoladorAlEquipo(CalendarioHabil calendario, Duration ventana, Transacciones transacciones,
			System.Logger log) {
		this.calendario = calendario;
		this.ventana = ventana;
		this.transacciones = transacciones;
		this.log = log;
	}

	/** @return desde cuándo se buscan los avisos, si ahora es esa hora */
	public Instant desde(Instant ahora) {
		return ahora.minus(ventana);
	}

	/** @return si lo ocurrido en ese momento se avisa: dentro de la ventana y en horario laboral */
	public boolean corresponde(Instant momento, Instant ahora) {
		return !momento.isBefore(desde(ahora)) && calendario.esHabil(momento);
	}

	/**
	 * Encola cada aviso en su propia transacción. Si uno falla, lo registra y sigue con los demás.
	 *
	 * @param avisos  los avisos que corresponden
	 * @param encolar cómo se encola uno
	 * @param queEs   cómo se describe uno en el registro de errores
	 */
	public <T> void encolar(Collection<T> avisos, Consumer<T> encolar, Function<T, String> queEs) {
		for (T aviso : avisos) {
			try {
				transacciones.ejecutar(() -> {
					encolar.accept(aviso);
					return null;
				});
			} catch (RuntimeException falla) {
				log.log(System.Logger.Level.ERROR, "No se pudo encolar " + queEs.apply(aviso), falla);
			}
		}
	}

}
