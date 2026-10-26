package cl.ucn.alertacuida.application.contacto.puerto;

import cl.ucn.alertacuida.domain.contacto.Correo;
import cl.ucn.alertacuida.domain.contacto.NumeroWhatsapp;

/**
 * Calcula whatsapp_hash y correo_hash, con los que se encuentra a una persona sin descifrar sus
 * datos (por ejemplo, cuando Meta informa un número).
 */
public interface HashMedioContacto {

	/** @return el hash; el mismo número da siempre el mismo hash */
	String de(NumeroWhatsapp numero);

	/** @return el hash; el mismo correo da siempre el mismo hash */
	String de(Correo correo);

}
