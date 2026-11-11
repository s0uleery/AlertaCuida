package cl.ucn.alertacuida.application.aviso.puerto;

import cl.ucn.alertacuida.application.aviso.modelo.AvisoPorEncolar;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Entrada a la bandeja de salida de correos (filas de registro_intento por el canal CORREO, pendientes
 * de envío). Encolar dos veces lo mismo no duplica el correo.
 */
public interface ColaCorreos {

	/**
	 * @param tipos qué tipos de alerta se avisan
	 * @param desde solo alertas recibidas desde esa hora
	 * @return cada par alerta y cuenta que corresponde avisar y aún no está encolado: alertas reales
	 *         (sin simulacros), no canceladas ni resueltas, de esos tipos; cuentas activas con
	 *         recibe_avisos, de cualquier rol
	 */
	List<AvisoPorEncolar> avisosAlEquipoPorEncolar(Set<TipoEvento> tipos, Instant desde);

	/**
	 * Encola el correo de una alerta para una cuenta del equipo, como contacto interno (lo crea si la
	 * cuenta aún no tiene uno). Debe llamarse dentro de una transacción.
	 */
	void encolarParaCuenta(UUID idAlerta, UUID idUsuario, Instant ahora);

	/**
	 * Encola el correo de respaldo de cada WhatsApp que falló desde esa hora, al mismo destino, si tiene
	 * correo y la alerta no está cancelada ni resuelta.
	 *
	 * @return cuántos encoló
	 */
	int encolarRespaldos(Instant desde, Instant ahora);

}
