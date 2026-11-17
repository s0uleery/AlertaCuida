package cl.ucn.alertacuida.application.escalamiento;

import cl.ucn.alertacuida.application.aviso.EncoladorAlEquipo;
import cl.ucn.alertacuida.application.aviso.modelo.Proposito;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.escalamiento.modelo.AvisoOficina;
import cl.ucn.alertacuida.application.escalamiento.puerto.AvisosOficina;
import cl.ucn.alertacuida.domain.calendario.CalendarioHabil;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Aviso a la oficina PRLAC: cada alerta de salud real, de cualquier gravedad, se avisa a la vez a todos los
 * coordinadores activos, en paralelo al escalamiento del reloj. A cada uno le llega un correo con el enlace para
 * confirmar que va y, si tiene opt-in vigente, también un WhatsApp; su confirmación detiene al reloj. Las técnicas
 * no van a la oficina: solo por correo a las cuentas con recibe_avisos ({@link
 * cl.ucn.alertacuida.application.aviso.EncolarCorreos}). Si la alerta pasa a grave después del aviso, se avisa de
 * nuevo; si llegó fuera de horario y se agrava en horario, se avisa al agravarse.
 *
 * <p>Solo si la alerta (o su agravamiento) ocurrió en horario laboral y dentro de la ventana: fuera de horario
 * no se envía nada, ni al empezar la jornada; la alerta queda en el dashboard.
 */
public class AvisarOficina {

	private static final System.Logger LOG = System.getLogger(AvisarOficina.class.getName());

	private final AvisosOficina avisos;
	private final EncoladorAlEquipo encolador;
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
		this.encolador = new EncoladorAlEquipo(calendario, ventana, transacciones, LOG);
		this.reloj = reloj;
	}

	/**
	 * Encola lo que corresponda una vez. Si en la misma pasada sale el aviso de una alerta, su agravamiento para
	 * esa cuenta no se encola: ese aviso ya dice grave. Si falla un aviso, lo registra y sigue con los demás.
	 */
	public void avisar() {
		Instant ahora = reloj.instant();
		List<AvisoOficina> enHorario = avisos.porEncolar(encolador.desde(ahora)).stream()
				.filter(aviso -> encolador.corresponde(aviso.momento(), ahora)).toList();
		Set<List<UUID>> conAviso = enHorario.stream().filter(aviso -> aviso.proposito() == Proposito.AVISO)
				.map(AvisarOficina::alertaYCuenta).collect(Collectors.toSet());
		encolador.encolar(enHorario.stream().filter(aviso -> !yaAvisadaEnEstaPasada(aviso, conAviso)).toList(),
				aviso -> avisos.encolar(aviso, ahora), aviso -> "el aviso " + aviso.proposito() + " de la alerta "
						+ aviso.idAlerta() + " para la cuenta " + aviso.idUsuario());
	}

	/** @return si es un agravamiento cuya alerta, para la misma cuenta, ya se avisa en esta pasada */
	private static boolean yaAvisadaEnEstaPasada(AvisoOficina aviso, Set<List<UUID>> conAviso) {
		return aviso.proposito() == Proposito.AGRAVAMIENTO && conAviso.contains(alertaYCuenta(aviso));
	}

	/** @return la alerta y la cuenta del aviso, para reconocer el aviso y el agravamiento de la misma pareja */
	private static List<UUID> alertaYCuenta(AvisoOficina aviso) {
		return List.of(aviso.idAlerta(), aviso.idUsuario());
	}

}
