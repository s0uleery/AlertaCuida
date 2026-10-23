package cl.ucn.alertacuida.adapters.web;

/**
 * Reglas de {@code @PreAuthorize} para los endpoints (1.3.7). Juntas aquí para no escribir el
 * permiso a mano en cada controlador: un error de tipeo dejaría el endpoint cerrado para todos.
 *
 * <p>Los permisos son "MODULO:ACCION", como en la tabla permiso_rol. Recordar que el módulo
 * USUARIOS son los pacientes; las cuentas del personal están en ADMINISTRACION.
 */
public final class Permisos {

	/** Crear cuentas del personal y emitir sus enlaces de activación. Solo el administrador lo tiene. */
	public static final String ADMINISTRACION_CREAR = "hasAuthority('ADMINISTRACION:CREAR')";

	/** Ver pacientes y el inventario de relojes. Coordinador y administrador. */
	public static final String USUARIOS_VER = "hasAuthority('USUARIOS:VER')";

	/** Registrar pacientes y relojes. */
	public static final String USUARIOS_CREAR = "hasAuthority('USUARIOS:CREAR')";

	/** Editar pacientes y relojes, y vincular, reemplazar o desvincular relojes. */
	public static final String USUARIOS_EDITAR = "hasAuthority('USUARIOS:EDITAR')";

	/** Dar de baja a un paciente (no se borra: queda su historial). */
	public static final String USUARIOS_ELIMINAR = "hasAuthority('USUARIOS:ELIMINAR')";

	private Permisos() {
	}

}
