package cl.ucn.alertacuida.application.contacto.puerto;

import cl.ucn.alertacuida.application.contacto.modelo.ContactoDelPaciente;
import cl.ucn.alertacuida.application.contacto.modelo.DatosPersona;
import cl.ucn.alertacuida.application.contacto.modelo.Persona;
import cl.ucn.alertacuida.application.contacto.modelo.TipoContacto;
import cl.ucn.alertacuida.application.contacto.modelo.Vinculo;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Personas de contacto y la lista de cada paciente (tablas contacto, contacto_paciente y
 * tipo_contacto). Nombre, WhatsApp y correo se cifran en el adaptador.
 */
public interface Contactos {

	/** @return los tipos de contacto activos, en el orden del catálogo */
	List<TipoContacto> tipos();

	/** @return todos los contactos del paciente, activos y desactivados, sin orden definido */
	List<ContactoDelPaciente> delPaciente(UUID idPaciente);

	/** @return la persona con ese hash de WhatsApp */
	Optional<Persona> personaPorWhatsapp(String whatsappHash);

	/** @return la persona con ese hash de correo */
	Optional<Persona> personaPorCorreo(String correoHash);

	/** @return el identificador de la persona creada, sin opt-in */
	UUID crearPersona(DatosPersona datos);

	/** Reemplaza nombre y medios de contacto de la persona (afecta a todos sus pacientes). */
	void actualizarPersona(UUID idContacto, DatosPersona datos);

	/**
	 * Guarda el opt-in de WhatsApp de la persona.
	 *
	 * @param fechaOptIn      cuándo lo aceptó, o vacío
	 * @param fechaRevocacion cuándo lo revocó, o vacío
	 */
	void guardarOptIn(UUID idContacto, Instant fechaOptIn, Instant fechaRevocacion);

	/** @return el identificador del vínculo creado (activo) */
	UUID vincular(UUID idPaciente, UUID idContacto, Vinculo vinculo, int prioridad);

	/** Reemplaza tipo, parentesco, cuidador principal y horario del vínculo. */
	void actualizarVinculo(UUID idContactoPaciente, Vinculo vinculo);

	/** Deja al paciente sin cuidador principal, para asignar otro. */
	void quitarCuidadorPrincipal(UUID idPaciente);

	/** Desactiva el vínculo y le quita el rol de cuidador principal. */
	void desactivar(UUID idContactoPaciente);

	/** Reactiva el vínculo con esa prioridad. */
	void reactivar(UUID idContactoPaciente, int prioridad);

	/**
	 * Da prioridad 1, 2, 3… a los vínculos activos, en ese orden.
	 *
	 * @param orden todos los vínculos activos del paciente
	 */
	void reordenar(UUID idPaciente, List<UUID> orden);

}
