package cl.ucn.alertacuida.adapters.seguridad.cifrado;

/**
 * Columnas que se guardan cifradas (las marcadas "cifrado" en las migraciones) que ya usa el
 * código. El nombre "tabla.columna" va como dato asociado del cifrado: un valor copiado a otra
 * columna no se puede descifrar.
 */
public enum ColumnaCifrada {

	/** RUN del personal. */
	USUARIO_RUT("usuario.rut"),
	/** RUN del paciente. */
	PACIENTE_RUT("paciente.rut"),
	/** Nombres del paciente. */
	PACIENTE_NOMBRES("paciente.nombres"),
	/** Apellidos del paciente. */
	PACIENTE_APELLIDOS("paciente.apellidos"),
	/** Fecha de nacimiento del paciente, en formato ISO antes de cifrar. */
	PACIENTE_FECHA_NACIMIENTO("paciente.fecha_nacimiento"),
	/** Dirección del paciente. */
	PACIENTE_DIRECCION("paciente.direccion"),
	/** Enfermedad o situación del paciente. */
	CONDICION_DESCRIPCION("condicion_paciente.descripcion"),
	/** Nombre de una persona de contacto. */
	CONTACTO_NOMBRE("contacto.nombre"),
	/** Número de WhatsApp de una persona de contacto, normalizado. */
	CONTACTO_WHATSAPP("contacto.whatsapp"),
	/** Correo de una persona de contacto, normalizado. */
	CONTACTO_CORREO("contacto.correo"),
	/** Motivo de un cambio en la configuración del paciente. */
	CONFIGURACION_MOTIVO("configuracion.motivo_cambio");

	private final String nombre;

	/** @param nombre "tabla.columna", como en la migración */
	ColumnaCifrada(String nombre) {
		this.nombre = nombre;
	}

	/** @return "tabla.columna" */
	public String nombre() {
		return nombre;
	}

}
