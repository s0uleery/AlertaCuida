package cl.ucn.alertacuida.application.aviso.modelo;

/**
 * Resultado reportado por WhatsApp al cerrar parcialmente una alerta (pendiente de validación definitiva por PRLAC).
 */
public enum ResultadoCierreWhatsapp {
	/** La persona está bien (tras llamada o en terreno). */
	FALSA_ALARMA,
	/** La persona requiere apoyo no urgente (coordinación con CESFAM / seguimiento PRLAC). */
	APOYO_NO_URGENTE,
	/** Situación de emergencia (llamar al 131 o derivación a SAMU). */
	EMERGENCIA
}
