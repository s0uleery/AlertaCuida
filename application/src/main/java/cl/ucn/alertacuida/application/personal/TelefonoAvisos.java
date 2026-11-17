package cl.ucn.alertacuida.application.personal;

import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.domain.contacto.NumeroWhatsapp;

/**
 * Regla del celular con WhatsApp de una cuenta del equipo, compartida por el alta y el cambio de
 * rol: el coordinador recibe los avisos de la oficina PRLAC, así que necesita uno.
 */
final class TelefonoAvisos {

	/** Rol que recibe por WhatsApp los avisos de la oficina PRLAC: necesita teléfono. */
	static final String ROL_AVISOS_OFICINA = "COORDINADOR";

	/** Solo métodos estáticos. */
	private TelefonoAvisos() {
	}

	/**
	 * @param codigoRol      rol que tendrá la cuenta
	 * @param telefono       lo que se escribió; puede venir vacío
	 * @param aceptaWhatsapp si la persona aceptó recibir WhatsApp
	 * @param yaTieneTelefono si la cuenta ya tiene uno guardado (en el alta, nunca)
	 * @return el teléfono normalizado, o vacío si no se escribió
	 * @throws SolicitudRechazada si no es un celular válido, un coordinador queda sin teléfono o se
	 *                            acepta WhatsApp sin teléfono
	 */
	static String validar(String codigoRol, String telefono, boolean aceptaWhatsapp, boolean yaTieneTelefono) {
		if (telefono == null || telefono.isBlank()) {
			if (ROL_AVISOS_OFICINA.equals(codigoRol) && !yaTieneTelefono) {
				throw new SolicitudRechazada("Los coordinadores necesitan un teléfono con WhatsApp: reciben los "
						+ "avisos de la oficina PRLAC");
			}
			if (aceptaWhatsapp) {
				throw new SolicitudRechazada("Para aceptar WhatsApp falta el teléfono");
			}
			return null;
		}
		try {
			return NumeroWhatsapp.de(telefono).valor();
		} catch (IllegalArgumentException invalido) {
			throw new SolicitudRechazada(invalido.getMessage());
		}
	}

}
