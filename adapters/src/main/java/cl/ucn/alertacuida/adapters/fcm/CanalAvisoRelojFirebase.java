package cl.ucn.alertacuida.adapters.fcm;

import cl.ucn.alertacuida.application.avisoreloj.error.TokenRelojRechazado;
import cl.ucn.alertacuida.application.avisoreloj.modelo.AvisoReloj;
import cl.ucn.alertacuida.application.avisoreloj.puerto.CanalAvisoReloj;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/** Envía los avisos por Firebase Cloud Messaging, de alta prioridad para despertar al reloj. */
public final class CanalAvisoRelojFirebase implements CanalAvisoReloj {

	/** Nombre propio de la app de Firebase dentro del proceso. */
	private static final String NOMBRE_APP = "alertacuida";

	/** Errores que significan que el token ya no sirve; el mensaje lo arma el servidor, así que no es por él. */
	private static final Set<MessagingErrorCode> TOKEN_INVALIDO =
			Set.of(MessagingErrorCode.UNREGISTERED, MessagingErrorCode.INVALID_ARGUMENT);

	private final FirebaseMessaging mensajeria;

	/** @param mensajeria cliente de Firebase ya autenticado */
	private CanalAvisoRelojFirebase(FirebaseMessaging mensajeria) {
		this.mensajeria = mensajeria;
	}

	/**
	 * @param rutaCredenciales archivo JSON de la cuenta de servicio, fuera del repositorio
	 * @return el canal autenticado
	 * @throws IllegalStateException si el archivo no se puede leer: así el backend no arranca a medias
	 */
	public static CanalAvisoRelojFirebase desdeArchivo(String rutaCredenciales) {
		try (InputStream llave = new FileInputStream(rutaCredenciales)) {
			FirebaseOptions opciones = FirebaseOptions.builder()
					.setCredentials(GoogleCredentials.fromStream(llave))
					.build();
			return new CanalAvisoRelojFirebase(FirebaseMessaging.getInstance(
					FirebaseApp.initializeApp(opciones, NOMBRE_APP)));
		} catch (IOException e) {
			throw new IllegalStateException("No se pudo leer la llave de Firebase (FCM_CREDENCIALES)", e);
		}
	}

	@Override
	public void enviar(AvisoReloj aviso) {
		Message mensaje = Message.builder()
				.setToken(aviso.token())
				.putAllData(aviso.datos())
				.setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
				.build();
		try {
			mensajeria.send(mensaje);
		} catch (FirebaseMessagingException e) {
			if (TOKEN_INVALIDO.contains(e.getMessagingErrorCode())) {
				throw new TokenRelojRechazado(e);
			}
			throw new IllegalStateException("Firebase no aceptó el aviso: " + e.getErrorCode(), e);
		}
	}

}
