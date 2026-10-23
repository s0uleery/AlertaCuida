package cl.ucn.alertacuida.adapters.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DynamicTest;

/**
 * Casos de prueba sobre la base: cada método devuelve una prueba que se ejecuta en orden,
 * en su propia transacción, sobre la misma conexión (los datos de un caso sirven a los siguientes).
 * En el SQL, {NOMBRE} se reemplaza por el identificador de prueba con ese nombre.
 */
final class Casos {

	/** Por qué debe rechazarse una operación: el código de error (SQLSTATE) de PostgreSQL. */
	enum Motivo {
		UNICO("23505"),
		CHECK("23514"),
		FORANEA("23503"),
		NULO("23502"),
		SUPERPOSICION("23P01");

		private final String sqlState;

		Motivo(String sqlState) {
			this.sqlState = sqlState;
		}
	}

	private static final Pattern MARCADOR = Pattern.compile("\\{([A-Z][A-Z0-9_]*)}");

	private final Connection conexion;
	private final Map<String, String> ids;

	Casos(Connection conexion, Map<String, String> ids) throws SQLException {
		this.conexion = conexion;
		this.ids = ids;
		conexion.setAutoCommit(false);
	}

	/** Reemplaza cada {NOMBRE} por su identificador. */
	String sql(String plantilla) {
		Matcher m = MARCADOR.matcher(plantilla);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			String id = ids.get(m.group(1));
			if (id == null) {
				throw new IllegalArgumentException("Identificador de prueba desconocido: " + m.group(1));
			}
			m.appendReplacement(sb, id);
		}
		m.appendTail(sb);
		return sb.toString();
	}

	/** Debe guardarse sin error. */
	DynamicTest acepta(String caso, String plantilla) {
		return DynamicTest.dynamicTest(caso, () -> {
			try (Statement st = conexion.createStatement()) {
				st.execute(sql(plantilla));
				conexion.commit();
			} catch (SQLException e) {
				conexion.rollback();
				fail("Se esperaba que se aceptara, pero se rechazó: " + e.getMessage());
			}
		});
	}

	/** Debe rechazarse, y por el motivo indicado. Se deshace siempre. */
	DynamicTest rechaza(String caso, String plantilla, Motivo motivo) {
		return DynamicTest.dynamicTest(caso, () -> {
			try (Statement st = conexion.createStatement()) {
				st.execute(sql(plantilla));
				st.execute("SET CONSTRAINTS ALL IMMEDIATE");
				conexion.rollback();
				fail("Se esperaba que se rechazara (" + motivo + "), pero se aceptó");
			} catch (SQLException e) {
				conexion.rollback();
				assertEquals(motivo.sqlState, e.getSQLState(), "Se rechazó por otro motivo: " + e.getMessage());
			}
		});
	}

	/**
	 * La base lo acepta a propósito: la nota del modelo dice que lo valida el backend.
	 * Se deshace siempre; deja constancia de que la regla no está en la base.
	 */
	DynamicTest backend(String caso, String plantilla) {
		return DynamicTest.dynamicTest("[lo valida el backend] " + caso, () -> {
			try (Statement st = conexion.createStatement()) {
				st.execute(sql(plantilla));
				st.execute("SET CONSTRAINTS ALL IMMEDIATE");
			} catch (SQLException e) {
				fail("La base lo rechazó, pero el modelo lo deja al backend: " + e.getMessage());
			} finally {
				conexion.rollback();
			}
		});
	}

	/** Una consulta debe devolver el valor esperado en la columna "n". */
	DynamicTest vale(String caso, String plantilla, Object esperado) {
		return DynamicTest.dynamicTest(caso, () -> {
			try (Statement st = conexion.createStatement(); ResultSet rs = st.executeQuery(sql(plantilla))) {
				Object obtenido = rs.next() ? rs.getObject("n") : null;
				conexion.commit();
				assertEquals(String.valueOf(esperado), String.valueOf(obtenido));
			} catch (SQLException e) {
				conexion.rollback();
				fail("Error en la consulta: " + e.getMessage());
			}
		});
	}

	/** Una sentencia debe afectar esa cantidad de filas. */
	DynamicTest filas(String caso, String plantilla, int esperado) {
		return DynamicTest.dynamicTest(caso, () -> {
			try (Statement st = conexion.createStatement()) {
				int obtenido = st.executeUpdate(sql(plantilla));
				conexion.commit();
				assertEquals(esperado, obtenido, "Filas afectadas");
			} catch (SQLException e) {
				conexion.rollback();
				fail("Error: " + e.getMessage());
			}
		});
	}

}
