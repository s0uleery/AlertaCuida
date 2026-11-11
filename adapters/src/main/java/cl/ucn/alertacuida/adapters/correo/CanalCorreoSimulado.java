package cl.ucn.alertacuida.adapters.correo;

import cl.ucn.alertacuida.application.aviso.modelo.Mensaje;
import cl.ucn.alertacuida.application.aviso.puerto.CanalNotificacion;
import java.util.UUID;

/**
 * Canal simulado para desarrollo: no envía nada, solo registra en el log que el correo habría salido.
 * El destinatario se registra enmascarado, porque el log no es lugar para datos personales.
 */
public class CanalCorreoSimulado implements CanalNotificacion {

	private static final System.Logger LOG = System.getLogger(CanalCorreoSimulado.class.getName());

	/** @return un identificador propio, como si el servidor lo hubiera aceptado */
	@Override
	public String enviar(Mensaje mensaje) {
		LOG.log(System.Logger.Level.INFO, "Correo simulado (no se envió) a " + enmascarar(mensaje.destinatario())
				+ ": " + mensaje.asunto());
		return "simulado-" + UUID.randomUUID();
	}

	/** @return el correo con la parte local oculta, ej. a***@muni.cl */
	static String enmascarar(String correo) {
		int arroba = correo.indexOf('@');
		return arroba <= 0 ? "***" : correo.charAt(0) + "***" + correo.substring(arroba);
	}

}
