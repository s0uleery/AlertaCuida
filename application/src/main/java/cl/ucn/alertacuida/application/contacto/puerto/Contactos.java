package cl.ucn.alertacuida.application.contacto.puerto;

import cl.ucn.alertacuida.application.contacto.modelo.DatosPersona;
import cl.ucn.alertacuida.application.contacto.modelo.Persona;
import cl.ucn.alertacuida.application.contacto.modelo.TipoContacto;
import cl.ucn.alertacuida.application.contacto.modelo.Vinculo;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Personas de contacto y la lista de cada paciente (tablas contacto, contacto_paciente, orden_contacto y
 * tipo_contacto). Nombre, WhatsApp y correo se cifran en el adaptador. La lista de un paciente viene de
 * {@link ListaDelPaciente}.
 */
public interface Contactos extends ListaDelPaciente {

	/** @return los tipos de contacto activos, en el orden del catálogo */
	List<TipoContacto> tipos();

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

	/** @return el identificador del vínculo creado (activo, todavía sin orden en ninguna gravedad) */
	UUID vincular(UUID idPaciente, UUID idContacto, Vinculo vinculo);

	/** Reemplaza tipo, parentesco, cuidador principal y horario del vínculo. */
	void actualizarVinculo(UUID idContactoPaciente, Vinculo vinculo);

	/** Deja al paciente sin cuidador principal, para asignar otro. */
	void quitarCuidadorPrincipal(UUID idPaciente);

	/** Desactiva el vínculo, le quita el rol de cuidador principal y lo saca de todas las gravedades. */
	void desactivar(UUID idContactoPaciente);

	/** Reactiva el vínculo, todavía sin orden en ninguna gravedad. */
	void reactivar(UUID idContactoPaciente);

	/**
	 * Deja en la lista de esa gravedad exactamente esos vínculos, con orden 1, 2, 3… Los demás vínculos del
	 * paciente salen de esa gravedad.
	 *
	 * @param orden vínculos activos del paciente, sin repetir; vacía deja la gravedad sin contactos
	 */
	void ordenar(UUID idPaciente, Severidad gravedad, List<UUID> orden);

}
