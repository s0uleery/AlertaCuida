package cl.ucn.alertacuida.soporte;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Deja vacía la base compartida de las pruebas de integración antes de cada caso.
 *
 * <p>Todas las clases de prueba usan la misma base y el orden en que corren cambia según el sistema (en el CI de
 * GitHub no es el mismo que en Windows). Por eso no basta con borrar las cuentas: otras pruebas dejan filas que
 * las referencian sin borrado en cascada (la atención y el seguimiento de una alerta, los contactos internos, la
 * auditoría). El orden de {@link #TABLAS} borra primero lo que cuelga de pacientes y relojes (con ellos se van en
 * cascada sus alertas, su atención y su seguimiento) y deja las cuentas para el final.
 */
public final class BaseDePrueba {

	/** Tablas en el orden en que se pueden borrar sin chocar con una clave foránea. */
	private static final String[] TABLAS = {"asignacion_reloj", "smartwatch", "paciente", "contacto",
			"registro_auditoria", "usuario"};

	private BaseDePrueba() {
	}

	/**
	 * Borra relojes, pacientes, contactos, auditoría y cuentas del equipo. Los catálogos de las migraciones
	 * (roles, permisos, tipos) quedan como están.
	 *
	 * @param jdbc cliente sobre la base de pruebas
	 */
	public static void vaciar(JdbcClient jdbc) {
		for (String tabla : TABLAS) {
			jdbc.sql("DELETE FROM " + tabla).update();
		}
	}

}
