package cl.ucn.alertacuida.application.escalamiento.puerto;

import cl.ucn.alertacuida.application.escalamiento.modelo.AlertaTrabada;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Alertas cuyo escalamiento quedó sin noticias del reloj, y el aviso del vigilante a los coordinadores. */
public interface EscalamientosTrabados {

	/**
	 * @param limite la última noticia debe ser de esa hora o antes
	 * @return las alertas reales del reloj (sin simulacros) que siguen escalando (recibidas o avisando a un
	 *         contacto: ni confirmadas, ni sin respuesta, ni resueltas, ni canceladas, ni esperando el día
	 *         hábil), sin noticias del reloj desde el límite y que el vigilante aún no avisó
	 */
	List<AlertaTrabada> trabadas(Instant limite);

	/**
	 * Encola el correo del vigilante para cada cuenta activa con rol COORDINADOR, como contacto interno (lo
	 * crea si la cuenta aún no tiene uno). Encolar dos veces no lo duplica. Debe llamarse en una transacción.
	 */
	void avisarCoordinadores(UUID idAlerta, Instant ahora);

}
