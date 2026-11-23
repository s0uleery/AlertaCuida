package cl.ucn.alertacuida.application.escalamiento.puerto;

import cl.ucn.alertacuida.application.escalamiento.modelo.AvisoOficina;
import java.time.Instant;
import java.util.List;

/**
 * Los avisos de la oficina PRLAC en la bandeja de salida (registro_intento, como contacto interno de cada
 * coordinador). Encolar dos veces lo mismo no duplica el aviso.
 */
public interface AvisosOficina {

	/**
	 * Solo alertas reales (sin simulacros), no canceladas ni resueltas, y cuentas activas con rol COORDINADOR.
	 * <ul>
	 * <li>Primer aviso: de cada alerta recibida desde esa hora, a cada coordinador.</li>
	 * <li>Agravamiento: de cada alerta que pasó a grave desde esa hora, a cada coordinador cuyo primer aviso se
	 * encoló antes de que se agravara (si se encoló después, ya decía grave).</li>
	 * </ul>
	 * El canal es WhatsApp si la cuenta tiene teléfono y opt-in vigente (dado y no revocado); si no, correo.
	 *
	 * @param desde solo lo ocurrido desde esa hora, para no avisar de alertas viejas
	 * @return los avisos que corresponden y aún no están en la bandeja, por ese canal y propósito
	 */
	List<AvisoOficina> porEncolar(Instant desde);

	/**
	 * Deja el aviso en la bandeja, pendiente de envío, como contacto interno de la cuenta (lo crea si aún no
	 * tiene uno). Debe llamarse dentro de una transacción.
	 */
	void encolar(AvisoOficina aviso, Instant ahora);

}
