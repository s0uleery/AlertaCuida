package cl.ucn.alertacuida.application.respuesta.puerto;

import java.util.Optional;
import java.util.UUID;

/**
 * Token de los enlaces de respuesta del correo al contacto: identifica el aviso y solo el servidor puede
 * crearlo, así que no hace falta guardarlo en la base. Va después de "#" en el enlace, para que no llegue a
 * los registros del servidor.
 */
public interface FirmaRespuesta {

	/** @return el token de ese aviso, seguro para usar en una dirección */
	String firmar(UUID idIntento);

	/** @return el aviso, si el token lo firmó este servidor; vacío si está alterado o mal formado */
	Optional<UUID> verificar(String token);

}
