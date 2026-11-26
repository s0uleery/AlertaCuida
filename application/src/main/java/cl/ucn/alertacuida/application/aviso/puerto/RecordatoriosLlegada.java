package cl.ucn.alertacuida.application.aviso.puerto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Alertas cuyo personal asignado por la oficina no ha llegado, y el correo recordatorio a su responsable
 * (registro_intento con propósito RECORDATORIO).
 */
public interface RecordatoriosLlegada {

	/**
	 * @param desde lo asignado antes no se recuerda (ventana del correo, por ejemplo tras una caída del servidor)
	 * @param hasta lo asignado después aún está en plazo
	 * @return las alertas abiertas con personal asignado en ese rango, sin llegada y sin recordatorio
	 */
	List<UUID> pendientes(Instant desde, Instant hasta);

	/** Deja en la bandeja de salida el correo recordatorio a quien tiene la alerta a cargo; si ya existe, nada. */
	void encolar(UUID idAlerta, Instant ahora);

}
