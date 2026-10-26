package cl.ucn.alertacuida.application.asignacion;

import cl.ucn.alertacuida.application.asignacion.modelo.RelojAutenticado;
import cl.ucn.alertacuida.application.asignacion.puerto.Asignaciones;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import java.util.Optional;

/**
 * Reconoce al reloj por su token (rol DEVICE). Solo sirve el token del vínculo vigente: al
 * desvincular, reemplazar el reloj o generar otro token, el anterior deja de servir.
 */
public class AutenticarReloj {

	private final Asignaciones asignaciones;
	private final SecretosUnSoloUso secretos;

	/** Recibe los puertos que usa; los arma ConfiguracionApiReloj. */
	public AutenticarReloj(Asignaciones asignaciones, SecretosUnSoloUso secretos) {
		this.asignaciones = asignaciones;
		this.secretos = secretos;
	}

	/**
	 * @param token el que envió el reloj en la cabecera Authorization
	 * @return el vínculo vigente de ese token, o vacío si no sirve
	 */
	public Optional<RelojAutenticado> autenticar(String token) {
		if (token == null || token.isBlank()) {
			return Optional.empty();
		}
		return asignaciones.vigentePorHashToken(secretos.hash(token));
	}

}
