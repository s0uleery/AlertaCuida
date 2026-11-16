package cl.ucn.alertacuida.application.escalamiento;

import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.escalamiento.modelo.AvisoOficina;
import cl.ucn.alertacuida.application.escalamiento.puerto.AvisosOficina;
import cl.ucn.alertacuida.domain.calendario.CalendarioHabil;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Aviso a la oficina PRLAC: cada alerta real, de salud o técnica y de cualquier gravedad, se avisa a la vez a
 * todos los coordinadores activos, en paralelo al escalamiento del reloj. Va por WhatsApp a quien tiene opt-in
 * vigente y por correo a quien no; si el WhatsApp falla, {@link
 * cl.ucn.alertacuida.application.aviso.EncolarCorreos} envía el correo de respaldo. Si la alerta pasa a grave
 * después del aviso, se avisa de nuevo.
 *
 * <p>Solo si la alerta (o su agravamiento) ocurrió en horario laboral y dentro de la ventana: fuera de horario
 * no se envía nada, ni al empezar la jornada; la alerta queda en el dashboard.
 */
public class AvisarOficina {

	private static final System.Logger LOG = System.getLogger(AvisarOficina.class.getName());

	private final AvisosOficina avisos;
	private final CalendarioHabil calendario;
	private final Duration ventana;
	private final Transacciones transacciones;
	private final Clock reloj;

	/**
	 * Recibe lo que usa; lo arma ConfiguracionEscalamiento.
	 *
	 * @param calendario horario laboral y feriados
	 * @param ventana    hasta qué tan atrás se buscan alertas y agravamientos
	 */
	public AvisarOficina(AvisosOficina avisos, CalendarioHabil calendario, Duration ventana,
			Transacciones transacciones, Clock reloj) {
		this.avisos = avisos;
		this.calendario = calendario;
		this.ventana = ventana;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/** Encola lo que corresponda una vez. Si falla un aviso, lo registra y sigue con los demás. */
	public void avisar() {
		Instant ahora = reloj.instant();
		for (AvisoOficina aviso : avisos.porEncolar(ahora.minus(ventana))) {
			if (calendario.esHabil(aviso.momento())) {
				encolarSinFallar(aviso, ahora);
			}
		}
	}

	/** Encola un aviso en su propia transacción; si falla, lo registra y sigue. */
	private void encolarSinFallar(AvisoOficina aviso, Instant ahora) {
		try {
			transacciones.ejecutar(() -> {
				avisos.encolar(aviso, ahora);
				return null;
			});
		} catch (RuntimeException falla) {
			LOG.log(System.Logger.Level.ERROR, "No se pudo encolar el aviso " + aviso.proposito() + " de la alerta "
					+ aviso.idAlerta() + " para la cuenta " + aviso.idUsuario(), falla);
		}
	}

}
