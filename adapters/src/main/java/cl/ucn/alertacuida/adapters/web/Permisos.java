package cl.ucn.alertacuida.adapters.web;

/**
 * Reglas de {@code @PreAuthorize} para los endpoints. Juntas aquí para no escribir el
 * permiso a mano en cada controlador: un error de tipeo dejaría el endpoint cerrado para todos.
 *
 * <p>Los permisos son "MODULO:ACCION", como en la tabla permiso_rol. Recordar que el módulo
 * USUARIOS son los pacientes; las cuentas del personal están en ADMINISTRACION.
 */
public final class Permisos {

	/** Ver la auditoría, las cuentas del equipo y los permisos de los roles. Solo el administrador. */
	public static final String ADMINISTRACION_VER = "hasAuthority('ADMINISTRACION:VER')";

	/** Crear cuentas del personal y emitir sus enlaces de activación. Solo el administrador lo tiene. */
	public static final String ADMINISTRACION_CREAR = "hasAuthority('ADMINISTRACION:CREAR')";

	/** Cambiar el rol y el aviso técnico de una cuenta, y los permisos de un rol. Solo el administrador. */
	public static final String ADMINISTRACION_EDITAR = "hasAuthority('ADMINISTRACION:EDITAR')";

	/** Ver pacientes y el inventario de relojes. Coordinador y administrador. */
	public static final String USUARIOS_VER = "hasAuthority('USUARIOS:VER')";

	/** Registrar pacientes y relojes. */
	public static final String USUARIOS_CREAR = "hasAuthority('USUARIOS:CREAR')";

	/** Editar pacientes y relojes, y vincular, reemplazar o desvincular relojes. */
	public static final String USUARIOS_EDITAR = "hasAuthority('USUARIOS:EDITAR')";

	/** Dar de baja a un paciente (no se borra: queda su historial). */
	public static final String USUARIOS_ELIMINAR = "hasAuthority('USUARIOS:ELIMINAR')";

	/** Ver las alertas y su línea de tiempo. */
	public static final String ALERTAS_VER = "hasAuthority('ALERTAS:VER')";

	/** Asumir y resolver alertas, y cerrar su seguimiento. */
	public static final String ALERTAS_EDITAR = "hasAuthority('ALERTAS:EDITAR')";

	/** Solo constantes: no se crean objetos de esta clase. */
	private Permisos() {
	}

}
