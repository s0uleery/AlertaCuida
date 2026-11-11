package cl.ucn.alertacuida.application.aviso.puerto;

import cl.ucn.alertacuida.application.aviso.error.EnvioFallido;
import cl.ucn.alertacuida.application.aviso.modelo.Mensaje;

/**
 * Un canal por el que el servidor envía avisos (la interfaz NotificationChannel del cronograma). Hoy
 * lo implementan el correo institucional (SMTP) y un canal simulado que solo registra el mensaje.
 */
public interface CanalNotificacion {

	/**
	 * @param mensaje lo que se envía
	 * @return el identificador del mensaje que dio el servidor, para seguir su entrega
	 * @throws EnvioFallido si no se pudo enviar
	 */
	String enviar(Mensaje mensaje);

}
