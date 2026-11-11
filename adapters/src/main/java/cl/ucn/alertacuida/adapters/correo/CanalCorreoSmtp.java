package cl.ucn.alertacuida.adapters.correo;

import cl.ucn.alertacuida.application.aviso.error.EnvioFallido;
import cl.ucn.alertacuida.application.aviso.modelo.Mensaje;
import cl.ucn.alertacuida.application.aviso.puerto.CanalNotificacion;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Correo institucional por SMTP (Spring Mail). El servidor, el usuario y la clave llegan como secreto
 * por variables de entorno (spring.mail.* en application.properties). Envía texto plano en UTF-8.
 */
public class CanalCorreoSmtp implements CanalNotificacion {

	private final JavaMailSender correo;
	private final String remitente;

	/**
	 * @param correo    cliente SMTP de Spring
	 * @param remitente la cuenta institucional desde la que salen los correos
	 */
	public CanalCorreoSmtp(JavaMailSender correo, String remitente) {
		this.correo = correo;
		this.remitente = remitente;
	}

	/**
	 * @return el Message-ID del correo, o uno propio si el servidor no lo dejó en el mensaje
	 * @throws EnvioFallido si el servidor no lo aceptó o no respondió
	 */
	@Override
	public String enviar(Mensaje mensaje) {
		try {
			MimeMessage mime = correo.createMimeMessage();
			MimeMessageHelper ayudante = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());
			ayudante.setFrom(remitente);
			ayudante.setTo(mensaje.destinatario());
			ayudante.setSubject(mensaje.asunto());
			ayudante.setText(mensaje.texto(), false);
			correo.send(mime);
			String id = mime.getMessageID();
			return id == null ? "correo-" + UUID.randomUUID() : id;
		} catch (MailException | MessagingException falla) {
			throw new EnvioFallido("No se pudo enviar el correo: " + falla.getMessage(), falla);
		}
	}

}
