package cl.ucn.alertacuida.application.escalamiento;

import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.escalamiento.modelo.AlertaTrabada;
import cl.ucn.alertacuida.application.escalamiento.puerto.EscalamientosTrabados;
import cl.ucn.alertacuida.domain.calendario.CalendarioHabil;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Vigilante del escalamiento: red de seguridad para cuando el reloj se apaga o pierde la señal a mitad del
 * recorrido de contactos. Si una alerta sigue escalando y el reloj lleva el plazo (15 minutos) sin pedir
 * avisos, encola un correo a todos los coordinadores activos, una sola vez por alerta. Distinto de
 * VigilarRelojes, que aplica las reglas de ausencia.
 *
 * <p>Solo avisa si el plazo se cumplió en horario laboral y dentro de la ventana (1 hora): fuera de horario no
 * se envía nada, ni al empezar la jornada, igual que los demás avisos a la oficina.
 */
public class VigilarEscalamiento {

	private static final System.Logger LOG = System.getLogger(VigilarEscalamiento.class.getName());

	private final EscalamientosTrabados trabados;
	private final CalendarioHabil calendario;
	private final Duration plazo;
	private final Duration ventana;
	private final Transacciones transacciones;
	private final Clock reloj;

	/**
	 * Recibe lo que usa; lo arma ConfiguracionEscalamiento.
	 *
	 * @param calendario horario laboral y feriados
	 * @param plazo      cuánto sin noticias del reloj se considera trabado
	 * @param ventana    hasta qué tan atrás puede haberse cumplido el plazo, para no avisar de alertas viejas
	 */
	public VigilarEscalamiento(EscalamientosTrabados trabados, CalendarioHabil calendario, Duration plazo,
			Duration ventana, Transacciones transacciones, Clock reloj) {
		this.trabados = trabados;
		this.calendario = calendario;
		this.plazo = plazo;
		this.ventana = ventana;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/** Revisa una vez. Si falla el aviso de una alerta, lo registra y sigue con las demás. */
	public void vigilar() {
		Instant ahora = reloj.instant();
		Instant desde = ahora.minus(ventana);
		for (AlertaTrabada alerta : trabados.trabadas(ahora.minus(plazo))) {
			Instant vencimiento = alerta.ultimaNoticia().plus(plazo);
			if (!vencimiento.isBefore(desde) && calendario.esHabil(vencimiento)) {
				avisarSinFallar(alerta, ahora);
			}
		}
	}

	/** Encola el aviso de una alerta en su propia transacción; si falla, lo registra y sigue. */
	private void avisarSinFallar(AlertaTrabada alerta, Instant ahora) {
		try {
			transacciones.ejecutar(() -> {
				trabados.avisarCoordinadores(alerta.idAlerta(), ahora);
				return null;
			});
		} catch (RuntimeException falla) {
			LOG.log(System.Logger.Level.ERROR, "No se pudo avisar a los coordinadores de la alerta trabada "
					+ alerta.idAlerta(), falla);
		}
	}

}
