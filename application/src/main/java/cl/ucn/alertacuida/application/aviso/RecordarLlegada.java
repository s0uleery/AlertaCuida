package cl.ucn.alertacuida.application.aviso;

import cl.ucn.alertacuida.application.aviso.puerto.RecordatoriosLlegada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Recordatorio N1-08 de los pasos de la oficina (decisión del 26-11-2026): si el personal asignado lleva el plazo
 * (30 minutos) sin que se registre su llegada, se envía un correo a quien tiene la alerta a cargo, una sola vez por
 * alerta. Su enlace abre la misma página, con "Ya llegó" y "Sigue en camino".
 *
 * <p>No mira el horario laboral: la oficina ya está atendiendo la alerta. Sí mira la ventana del correo, para no
 * recordar asignaciones viejas después de una caída del servidor.
 */
public class RecordarLlegada {

	private static final System.Logger LOG = System.getLogger(RecordarLlegada.class.getName());

	private final RecordatoriosLlegada recordatorios;
	private final Duration plazo;
	private final Duration ventana;
	private final Transacciones transacciones;
	private final Clock reloj;

	/**
	 * Recibe lo que usa; lo arma ConfiguracionRespuestaContacto.
	 *
	 * @param plazo   cuánto puede tardar el personal antes del recordatorio
	 * @param ventana hasta qué tan atrás puede haberse cumplido el plazo
	 */
	public RecordarLlegada(RecordatoriosLlegada recordatorios, Duration plazo, Duration ventana,
			Transacciones transacciones, Clock reloj) {
		this.recordatorios = recordatorios;
		this.plazo = plazo;
		this.ventana = ventana;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/** Revisa una vez. Cada recordatorio va en su propia transacción: si uno falla, se registra y sigue. */
	public void recordar() {
		Instant ahora = reloj.instant();
		Instant hasta = ahora.minus(plazo);
		for (UUID alerta : recordatorios.pendientes(hasta.minus(ventana), hasta)) {
			try {
				transacciones.ejecutar(() -> {
					recordatorios.encolar(alerta, ahora);
					return null;
				});
			} catch (RuntimeException falla) {
				LOG.log(System.Logger.Level.ERROR, "No se pudo encolar el recordatorio de llegada de la alerta "
						+ alerta, falla);
			}
		}
	}

}
