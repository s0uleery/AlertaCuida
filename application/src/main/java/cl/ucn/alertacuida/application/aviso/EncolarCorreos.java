package cl.ucn.alertacuida.application.aviso;

import cl.ucn.alertacuida.application.aviso.modelo.AvisoPorEncolar;
import cl.ucn.alertacuida.application.aviso.puerto.ColaCorreos;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import cl.ucn.alertacuida.domain.calendario.CalendarioHabil;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Decide qué correos se envían y los deja en la bandeja de salida:
 * <ul>
 * <li>a las cuentas con recibe_avisos, de cualquier rol (los coordinadores también, además de su
 * WhatsApp), por las alertas técnicas, solo si la alerta ocurrió en horario laboral (fuera de él no se
 * envía nada, ni al empezar la jornada). Las de salud no: a quién se avisa lo decide el reloj;</li>
 * <li>el respaldo de cada WhatsApp fallido, a cualquier hora.</li>
 * </ul>
 * Solo mira lo ocurrido dentro de la ventana, para no avisar de alertas viejas (por ejemplo, tras
 * encender recibe_avisos o después de que el servidor estuvo detenido).
 */
public class EncolarCorreos {

	private static final System.Logger LOG = System.getLogger(EncolarCorreos.class.getName());

	private final ColaCorreos cola;
	private final CalendarioHabil calendario;
	private final Duration ventana;
	private final Transacciones transacciones;
	private final Clock reloj;

	/**
	 * Recibe lo que usa; lo arma ConfiguracionCorreo.
	 *
	 * @param calendario horario laboral y feriados
	 * @param ventana    hasta qué tan atrás se buscan alertas y WhatsApp fallidos
	 */
	public EncolarCorreos(ColaCorreos cola, CalendarioHabil calendario, Duration ventana, Transacciones transacciones,
			Clock reloj) {
		this.cola = cola;
		this.calendario = calendario;
		this.ventana = ventana;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/** Encola lo que corresponda una vez. Si falla una cuenta, registra el error y sigue con las demás. */
	public void encolar() {
		Instant ahora = reloj.instant();
		Instant desde = ahora.minus(ventana);
		for (AvisoPorEncolar aviso : cola.avisosAlEquipoPorEncolar(TipoEvento.tecnicas(), desde)) {
			if (calendario.esHabil(aviso.momento())) {
				encolarSinFallar(aviso, ahora);
			}
		}
		cola.encolarRespaldos(desde, ahora);
	}

	/** Encola el correo a una cuenta en su propia transacción; si falla, lo registra y sigue. */
	private void encolarSinFallar(AvisoPorEncolar aviso, Instant ahora) {
		try {
			transacciones.ejecutar(() -> {
				cola.encolarParaCuenta(aviso.idAlerta(), aviso.idUsuario(), ahora);
				return null;
			});
		} catch (RuntimeException falla) {
			LOG.log(System.Logger.Level.ERROR, "No se pudo encolar el correo de la alerta " + aviso.idAlerta()
					+ " para la cuenta " + aviso.idUsuario(), falla);
		}
	}

}
