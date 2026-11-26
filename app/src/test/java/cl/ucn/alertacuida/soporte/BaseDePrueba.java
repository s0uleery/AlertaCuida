package cl.ucn.alertacuida.soporte;

import java.util.UUID;
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
	private static final String[] TABLAS = {"emparejamiento_reloj", "asignacion_reloj", "smartwatch", "paciente",
			"contacto", "registro_auditoria", "usuario"};

	private BaseDePrueba() {
	}

	/**
	 * Borra los códigos de emparejamiento, relojes, pacientes, contactos, auditoría y cuentas del equipo. Los
	 * catálogos de las migraciones (roles, permisos, tipos) quedan como están.
	 *
	 * @param jdbc cliente sobre la base de pruebas
	 */
	public static void vaciar(JdbcClient jdbc) {
		for (String tabla : TABLAS) {
			jdbc.sql("DELETE FROM " + tabla).update();
		}
	}

	/**
	 * Agrega una persona ya guardada a la lista de un paciente, activa y con el mismo lugar en las tres
	 * gravedades (tabla orden_contacto).
	 *
	 * @param tipo  código de tipo_contacto, por ejemplo TERCERO_SIGNIFICATIVO u OTRO
	 * @param orden su lugar en leve, media y grave (1 = primero)
	 * @return el identificador del vínculo (contacto_paciente)
	 */
	public static UUID vincular(JdbcClient jdbc, UUID paciente, UUID persona, String tipo, int orden) {
		return vincular(jdbc, paciente, persona, tipo, orden, true);
	}

	/**
	 * Como {@link #vincular(JdbcClient, UUID, UUID, String, int)}; un vínculo desactivado queda sin orden en
	 * ninguna gravedad.
	 *
	 * @param activo false para dejarlo desactivado
	 */
	public static UUID vincular(JdbcClient jdbc, UUID paciente, UUID persona, String tipo, int orden,
			boolean activo) {
		UUID vinculo = jdbc.sql("""
				INSERT INTO contacto_paciente (id_paciente, id_contacto, codigo_tipo_contacto, activo)
				VALUES (:paciente, :persona, :tipo, :activo) RETURNING id_contacto_paciente""")
				.param("paciente", paciente).param("persona", persona).param("tipo", tipo).param("activo", activo)
				.query(UUID.class).single();
		if (activo) {
			jdbc.sql("""
					INSERT INTO orden_contacto (id_contacto_paciente, severidad, orden)
					SELECT :vinculo, g, :orden FROM unnest(ARRAY['LEVE', 'MEDIA', 'GRAVE']) AS g""")
					.param("vinculo", vinculo).param("orden", orden)
					.update();
		}
		return vinculo;
	}

}
