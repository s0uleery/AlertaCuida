package cl.ucn.alertacuida.application.personal;

import cl.ucn.alertacuida.application.aviso.PlantillasCorreo;
import cl.ucn.alertacuida.application.aviso.puerto.CanalNotificacion;
import cl.ucn.alertacuida.application.personal.modelo.CuentaPendiente;
import cl.ucn.alertacuida.application.personal.modelo.EnlaceActivacion;
import cl.ucn.alertacuida.application.personal.modelo.EnvioEnlace;
import cl.ucn.alertacuida.application.personal.puerto.CuentasPersonal;
import java.util.Optional;

/**
 * Envía por correo el enlace de activación a la persona a la que se le creó la cuenta (o se le emitió uno
 * nuevo), con la plantilla "activacion" de correos.yml. Así solo ella conoce el enlace y elige su contraseña.
 *
 * <p>Se llama después de guardar la cuenta: si el correo falla, la cuenta queda creada igual. Un envío fallido
 * no se reintenta solo; quien administra ve el enlace en el dashboard para pasárselo por otra vía, o pide uno
 * nuevo, que se vuelve a enviar.
 */
public class EnviarEnlaceActivacion {

	private static final System.Logger LOG = System.getLogger(EnviarEnlaceActivacion.class.getName());

	private final CuentasPersonal cuentas;
	private final PlantillasCorreo plantillas;
	private final CanalNotificacion canal;
	private final String urlActivacion;

	/**
	 * Recibe lo que usa; lo arma ConfiguracionCasosDeUso.
	 *
	 * @param canal         correo institucional o canal simulado
	 * @param urlActivacion página de activación del dashboard; el token va después de "#"
	 */
	public EnviarEnlaceActivacion(CuentasPersonal cuentas, PlantillasCorreo plantillas, CanalNotificacion canal,
			String urlActivacion) {
		this.cuentas = cuentas;
		this.plantillas = plantillas;
		this.canal = canal;
		this.urlActivacion = urlActivacion;
	}

	/**
	 * @param enlace el recién emitido, con su token
	 * @return el enlace completo y si salió por correo
	 */
	public EnvioEnlace enviar(EnlaceActivacion enlace) {
		String url = urlActivacion + "#" + enlace.token();
		Optional<CuentaPendiente> cuenta = cuentas.buscarPendiente(enlace.idUsuario());
		boolean enviado = cuenta.isPresent() && enviarA(cuenta.get(), url, enlace);
		return new EnvioEnlace(enlace.idUsuario(), url, enlace.expiraEn(), enviado);
	}

	/** @return si el correo salió de verdad; un error no se propaga, porque la cuenta ya está creada */
	private boolean enviarA(CuentaPendiente cuenta, String url, EnlaceActivacion enlace) {
		if (cuenta.correo() == null || cuenta.correo().isBlank()) {
			return false;
		}
		try {
			canal.enviar(plantillas.activacion(cuenta.correo(), cuenta.nombre(), url, enlace.expiraEn()));
			return canal.entregaDeVerdad();
		} catch (RuntimeException falla) {
			LOG.log(System.Logger.Level.WARNING, "No se pudo enviar el correo de activación de la cuenta "
					+ enlace.idUsuario() + ": " + falla.getMessage());
			return false;
		}
	}

}
