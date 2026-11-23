package cl.ucn.alertacuida.adapters.persistencia.alerta;

import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.application.alerta.modelo.EvidenciaAlerta;
import cl.ucn.alertacuida.application.alerta.modelo.LecturaEvidencia;
import cl.ucn.alertacuida.application.alerta.modelo.UbicacionAlerta;
import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lectura de alerta.evidencia para la ficha de la alerta. Los casos salen del formato del contrato del reloj
 * (esquema Evidencia de docs/api-reloj.yaml), de lo que guardan las alertas técnicas del servidor y de la
 * decisión del 19-11-2026: la ficha muestra lecturas, batería, inmovilidad, ubicación y el dato de cada
 * técnica, y nunca falla por una evidencia dañada.
 */
class LectorEvidenciaTest {

	private static final Instant MEDICION = Instant.parse("2026-11-19T12:00:00Z");

	private final LectorEvidencia lector = new LectorEvidencia(JsonMapper.builder().build());

	/** La evidencia completa del reloj sale campo por campo, sin la celda de la matriz. */
	@Test
	void leeLaEvidenciaDelReloj() {
		EvidenciaAlerta evidencia = lector.leer("""
				{"lecturas": [{"tipo": "FC", "valor": 128, "fechaHoraMedicion": "2026-11-19T12:00:00Z"},
					{"tipo": "SPO2", "valor": 88.5, "fechaHoraMedicion": "2026-11-19T12:00:00Z"}],
				"nivelBateria": 54, "segundosInmovilidad": 60, "celdaMatriz": "X",
				"ubicacion": {"latitud": -29.95, "longitud": -71.34, "precisionMetros": 15,
					"fechaHoraMedicion": "2026-11-19T12:00:00Z"}}""");

		assertThat(evidencia.lecturas()).containsExactly(new LecturaEvidencia(TipoLectura.FC, 128, MEDICION),
				new LecturaEvidencia(TipoLectura.SPO2, 88.5, MEDICION));
		assertThat(evidencia.nivelBateria()).isEqualTo(54);
		assertThat(evidencia.segundosInmovilidad()).isEqualTo(60);
		assertThat(evidencia.ubicacion()).isEqualTo(new UbicacionAlerta(-29.95, -71.34, 15.0, MEDICION));
	}

	/** Sin evidencia guardada, todo vacío. */
	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"null", "{}", "  "})
	void sinEvidenciaTodoVacio(String json) {
		assertThat(lector.leer(json)).isEqualTo(EvidenciaAlerta.VACIA);
	}

	/** Un JSON roto o que no es un objeto no rompe la ficha: se muestra sin evidencia. */
	@ParameterizedTest
	@ValueSource(strings = {"{\"lecturas\": [", "[1, 2]", "\"texto\"", "42"})
	void unaEvidenciaDanadaSeMuestraVacia(String json) {
		assertThat(lector.leer(json)).isEqualTo(EvidenciaAlerta.VACIA);
	}

	/** Un campo con un tipo inesperado se omite y el resto se muestra igual. */
	@Test
	void unCampoRaroSeOmiteSinPerderLosDemas() {
		EvidenciaAlerta evidencia = lector.leer("""
				{"nivelBateria": "alto", "segundosInmovilidad": 30,
				"lecturas": [{"tipo": "PRESION", "valor": 1, "fechaHoraMedicion": "2026-11-19T12:00:00Z"},
					{"tipo": "FC", "valor": "x", "fechaHoraMedicion": "2026-11-19T12:00:00Z"},
					{"tipo": "FC", "valor": 70, "fechaHoraMedicion": "ayer"},
					{"tipo": "FC", "valor": 72, "fechaHoraMedicion": "2026-11-19T12:00:00Z"}],
				"ubicacion": {"longitud": -71.34, "fechaHoraMedicion": "2026-11-19T12:00:00Z"}}""");

		assertThat(evidencia.nivelBateria()).isNull();
		assertThat(evidencia.segundosInmovilidad()).isEqualTo(30);
		assertThat(evidencia.lecturas()).containsExactly(new LecturaEvidencia(TipoLectura.FC, 72, MEDICION));
		assertThat(evidencia.ubicacion()).isNull();
	}

	/** Una ubicación sin precisión se muestra igual, con la precisión vacía. */
	@Test
	void laPrecisionEsOpcional() {
		EvidenciaAlerta evidencia = lector.leer("""
				{"ubicacion": {"latitud": -29.95, "longitud": -71.34, "fechaHoraMedicion": "2026-11-19T12:00:00Z"}}""");

		assertThat(evidencia.ubicacion()).isEqualTo(new UbicacionAlerta(-29.95, -71.34, null, MEDICION));
	}

	/** Exceso de alertas: cuántas llegaron y en qué ventana. */
	@Test
	void leeElExcesoDeAlertas() {
		EvidenciaAlerta evidencia = lector.leer("{\"alertas\": 12, \"ventanaMinutos\": 60}");

		assertThat(evidencia.alertasEnVentana()).isEqualTo(12);
		assertThat(evidencia.ventanaMinutos()).isEqualTo(60);
	}

	/** Reloj no puesto: desde cuándo. */
	@Test
	void leeDesdeCuandoNoEstaPuesto() {
		assertThat(lector.leer("{\"desde\": \"2026-11-19T12:00:00Z\"}").noPuestoDesde()).isEqualTo(MEDICION);
	}

	/** Silencio de datos: la última sincronización, o vacía si nunca sincronizó. */
	@Test
	void leeLaUltimaSincronizacion() {
		assertThat(lector.leer("{\"ultimaSincronizacion\": \"2026-11-19T12:00:00Z\"}").ultimaSincronizacion())
				.isEqualTo(MEDICION);
		assertThat(lector.leer("{\"ultimaSincronizacion\": null}").ultimaSincronizacion()).isNull();
	}

}
