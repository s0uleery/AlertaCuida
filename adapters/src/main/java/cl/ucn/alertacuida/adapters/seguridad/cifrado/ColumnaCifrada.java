package cl.ucn.alertacuida.adapters.seguridad.cifrado;

/**
 * Columnas que se guardan cifradas (las marcadas "cifrado" en las migraciones) que ya usa el
 * código. El nombre "tabla.columna" va como dato asociado del cifrado: un valor copiado a otra
 * columna no se puede descifrar.
 */
public enum ColumnaCifrada {

	/** RUN del personal. */
	USUARIO_RUT("usuario.rut"),
	/** Teléfono (WhatsApp) del personal, normalizado. */
	USUARIO_TELEFONO("usuario.telefono"),
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
	CONFIGURACION_MOTIVO("configuracion.motivo_cambio"),
	/** Latitud del centro de la zona segura (suele ser el domicilio), como texto antes de cifrar. */
	ZONA_SEGURA_LATITUD("zona_segura.latitud"),
	/** Longitud del centro de la zona segura, como texto antes de cifrar. */
	ZONA_SEGURA_LONGITUD("zona_segura.longitud"),
	/** Lo que anotó quien resolvió la alerta. */
	ATENCION_OBSERVACIONES("atencion_alerta.observaciones_cierre"),
	/** Nota de una acción de seguimiento del caso. */
	SEGUIMIENTO_NOTA("seguimiento.nota");

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
