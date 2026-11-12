package cl.ucn.alertacuida.application.avisoreloj.modelo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Un aviso listo para salir hacia un reloj. Solo lleva identificadores, nunca datos de salud.
 *
 * @param token         token de Firebase del reloj destino
 * @param idAviso       para que el reloj acuse recibo; null en CONFIGURACION_NUEVA
 * @param eventIdAlerta la alerta de la que se habla; null en CONFIGURACION_NUEVA
 * @param idContacto    quién confirmó; solo en ALERTA_CONFIRMADA
 */
public record AvisoReloj(String token, TipoAvisoReloj tipo, UUID idAviso, UUID eventIdAlerta, UUID idContacto) {

	/** @return un aviso de que hay una configuración nueva */
	public static AvisoReloj configuracionNueva(String token) {
		return new AvisoReloj(token, TipoAvisoReloj.CONFIGURACION_NUEVA, null, null, null);
	}

	/** @return el contenido del mensaje, como lo define el contrato con el reloj (esquema AvisoFcm) */
	public Map<String, String> datos() {
		Map<String, String> datos = new LinkedHashMap<>();
		datos.put("tipo", tipo.name());
		agregar(datos, "idAviso", idAviso);
		agregar(datos, "eventIdAlerta", eventIdAlerta);
		agregar(datos, "idContacto", idContacto);
		return Map.copyOf(datos);
	}

	/** Agrega el identificador si el aviso lo lleva. */
	private static void agregar(Map<String, String> datos, String clave, UUID valor) {
		if (valor != null) {
			datos.put(clave, valor.toString());
		}
	}

}
