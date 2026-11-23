package cl.ucn.alertacuida.application.aviso.puerto;

import cl.ucn.alertacuida.application.aviso.modelo.CorreoPendiente;
import java.time.Instant;
import java.util.List;

/**
 * Salida de la bandeja de correos: los que toca enviar y, por {@link RegistroEnvios}, lo que pasó con cada
 * uno (columnas de envío, reintento y fallo de registro_intento).
 */
public interface BandejaSalida extends RegistroEnvios {

	/** @return los correos cuyo próximo envío ya llegó, el más antiguo primero */
	List<CorreoPendiente> pendientes(Instant ahora);

}
