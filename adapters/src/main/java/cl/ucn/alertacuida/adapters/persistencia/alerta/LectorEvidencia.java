package cl.ucn.alertacuida.adapters.persistencia.alerta;

import cl.ucn.alertacuida.application.alerta.modelo.EvidenciaAlerta;
import cl.ucn.alertacuida.application.alerta.modelo.LecturaEvidencia;
import cl.ucn.alertacuida.application.alerta.modelo.UbicacionAlerta;
import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Convierte alerta.evidencia (texto JSON) en lo que muestra la ficha de la alerta. Cada campo se lee por
 * separado: si uno no tiene el tipo esperado se omite y el resto se muestra igual. Un JSON que no se puede
 * leer deja la ficha sin evidencia, nunca la hace fallar.
 */
class LectorEvidencia {

	private final ObjectMapper json;

	/** @param json lee el texto JSON */
	LectorEvidencia(ObjectMapper json) {
		this.json = json;
	}

	/**
	 * @param evidencia el texto de alerta.evidencia; puede ser null
	 * @return lo que se pudo leer; {@link EvidenciaAlerta#VACIA} si no hay nada o no es un objeto JSON
	 */
	EvidenciaAlerta leer(String evidencia) {
		if (evidencia == null || evidencia.isBlank()) {
			return EvidenciaAlerta.VACIA;
		}
		JsonNode raiz;
		try {
			raiz = json.readTree(evidencia);
		} catch (JacksonException e) {
			return EvidenciaAlerta.VACIA;
		}
		if (raiz == null || !raiz.isObject()) {
			return EvidenciaAlerta.VACIA;
		}
		return new EvidenciaAlerta(lecturas(raiz.path("lecturas")), entero(raiz, "nivelBateria"),
				entero(raiz, "segundosInmovilidad"), ubicacion(raiz.path("ubicacion")), entero(raiz, "alertas"),
				entero(raiz, "ventanaMinutos"), instante(raiz, "desde"), instante(raiz, "ultimaSincronizacion"));
	}

	/** @return las lecturas completas y de un tipo conocido; las demás se omiten */
	private static List<LecturaEvidencia> lecturas(JsonNode nodo) {
		List<LecturaEvidencia> lecturas = new ArrayList<>();
		if (!nodo.isArray()) {
			return lecturas;
		}
		for (JsonNode lectura : nodo.values()) {
			TipoLectura tipo = tipo(lectura.path("tipo"));
			Double valor = decimal(lectura, "valor");
			Instant medicion = instante(lectura, "fechaHoraMedicion");
			if (tipo != null && valor != null && medicion != null) {
				lecturas.add(new LecturaEvidencia(tipo, valor, medicion));
			}
		}
		return lecturas;
	}

	/** @return la ubicación si trae latitud, longitud y hora; si no, null */
	private static UbicacionAlerta ubicacion(JsonNode nodo) {
		Double latitud = decimal(nodo, "latitud");
		Double longitud = decimal(nodo, "longitud");
		Instant medicion = instante(nodo, "fechaHoraMedicion");
		if (latitud == null || longitud == null || medicion == null) {
			return null;
		}
		return new UbicacionAlerta(latitud, longitud, decimal(nodo, "precisionMetros"), medicion);
	}

	/** @return el tipo de lectura, o null si no es uno conocido */
	private static TipoLectura tipo(JsonNode nodo) {
		if (!nodo.isString()) {
			return null;
		}
		try {
			return TipoLectura.valueOf(nodo.asString());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** @return el número entero de ese campo, o null si falta o no es entero */
	private static Integer entero(JsonNode padre, String campo) {
		JsonNode nodo = padre.path(campo);
		return nodo.isIntegralNumber() && nodo.canConvertToInt() ? nodo.asInt() : null;
	}

	/** @return el número de ese campo, o null si falta o no es un número */
	private static Double decimal(JsonNode padre, String campo) {
		JsonNode nodo = padre.path(campo);
		return nodo.isNumber() ? nodo.asDouble() : null;
	}

	/** @return la fecha ISO-8601 de ese campo, o null si falta o no es una fecha */
	private static Instant instante(JsonNode padre, String campo) {
		JsonNode nodo = padre.path(campo);
		if (!nodo.isString()) {
			return null;
		}
		try {
			return Instant.parse(nodo.asString());
		} catch (DateTimeParseException e) {
			return null;
		}
	}

}
