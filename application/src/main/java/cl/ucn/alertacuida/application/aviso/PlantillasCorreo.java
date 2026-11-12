package cl.ucn.alertacuida.application.aviso;

import cl.ucn.alertacuida.application.aviso.modelo.CorreoPendiente;
import cl.ucn.alertacuida.application.aviso.modelo.Mensaje;
import cl.ucn.alertacuida.application.aviso.modelo.TextosCorreo;
import cl.ucn.alertacuida.application.respuesta.puerto.FirmaRespuesta;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redacta los correos con los textos editables de correos.yml, sin datos clínicos: del paciente solo va su
 * alias (o, si no tiene, el folio de la alerta), y de la alerta, su folio y su gravedad. El tipo de una
 * alerta de salud no se nombra; el de una técnica sí, porque habla del reloj y no de la persona.
 * <ul>
 * <li>Silencio de datos, al equipo.</li>
 * <li>Alerta, al equipo: las graves y el respaldo de un WhatsApp a la oficina.</li>
 * <li>Aviso a un contacto del paciente (respaldo de su WhatsApp): en las graves, le indica llamar al 131.
 * Lleva un solo enlace a la página /responder del dashboard, donde elige "Confirmo que voy" o "No puedo
 * asistir", con el token firmado del aviso después de "#"; sin ese marcador en su cuerpo, el backend no
 * arranca.</li>
 * </ul>
 * Las graves van marcadas como importantes. Los textos se revisan al crear las plantillas: un marcador
 * desconocido o un texto que falta impide arrancar el backend, para que nunca salga un correo a medias.
 */
public class PlantillasCorreo {

	/** Un marcador: una palabra entre llaves, como {folio} o {Reloj}. */
	private static final Pattern MARCADOR = Pattern.compile("\\{([A-Za-z]+)}");

	/** Tres o más saltos de línea (con espacios entre medio): quedan cuando un párrafo se vacía. */
	private static final Pattern LINEAS_DE_MAS = Pattern.compile("\\n[ \\t]*(\\n[ \\t]*){2,}");

	/** El marcador del enlace de respuesta, obligatorio en el cuerpo del correo al contacto. */
	private static final Set<String> ENLACE = Set.of("responder");

	private final String urlDashboard;
	private final TextosCorreo textos;
	private final FirmaRespuesta firma;

	/**
	 * @param urlDashboard dirección del dashboard, para que el equipo abra la alerta y el contacto responda
	 * @param textos       los textos de correos.yml
	 * @param firma        crea el token del enlace de respuesta del contacto
	 * @throws IllegalArgumentException si un texto falta o usa un marcador que no existe en su plantilla, si
	 *                                  al cuerpo del contacto le falta el enlace de respuesta, o si falta el
	 *                                  nombre de una gravedad o de una alerta técnica
	 */
	public PlantillasCorreo(String urlDashboard, TextosCorreo textos, FirmaRespuesta firma) {
		this.urlDashboard = urlDashboard;
		this.textos = textos;
		this.firma = firma;
		revisar(textos);
	}

	/** @return el mensaje que corresponde a ese correo, según su destino y su alerta */
	public Mensaje redactar(CorreoPendiente correo) {
		if (!correo.alEquipo()) {
			return aContacto(correo);
		}
		return correo.tipo() == TipoEvento.SILENCIO_DATOS ? silencioDeDatos(correo) : alertaAlEquipo(correo);
	}

	/** Silencio de datos: el reloj dejó de sincronizar. */
	private Mensaje silencioDeDatos(CorreoPendiente correo) {
		TextosCorreo.SilencioDatos t = textos.silencioDatos();
		String reloj = tieneAlias(correo)
				? rellenar(t.relojConAlias(), Map.of("alias", alias(correo)))
				: t.relojSinAlias();
		Map<String, String> valores = Map.of("reloj", reloj, "folio", folio(correo), "dashboard", urlDashboard,
				"pie", textos.pieEquipo());
		return mensaje(correo, t.asunto(), t.cuerpo(), valores);
	}

	/** Una alerta grave, o el respaldo de un WhatsApp a la oficina por cualquier alerta. */
	private Mensaje alertaAlEquipo(CorreoPendiente correo) {
		TextosCorreo.AlertaEquipo t = textos.alertaEquipo();
		String tecnica = textos.tecnicas().get(correo.tipo());
		String alerta = tecnica == null
				? rellenar(t.alertaSalud(), Map.of("gravedad", textos.gravedades().get(correo.severidad()),
						"folio", folio(correo)))
				: rellenar(t.alertaTecnica(), Map.of("tecnica", tecnica, "folio", folio(correo)));
		String deQuien = tieneAlias(correo) ? rellenar(t.deQuienConAlias(), Map.of("alias", alias(correo))) : "";
		Map<String, String> valores = Map.of("alerta", alerta, "deQuien", deQuien, "folio", folio(correo),
				"dashboard", urlDashboard, "pie", textos.pieEquipo());
		return mensaje(correo, t.asunto(), t.cuerpo(), valores);
	}

	/**
	 * Aviso a un contacto del paciente: comunicarse con la persona y, si es grave y no puede, llamar al 131.
	 * Lleva el enlace para responder si va o no.
	 */
	private Mensaje aContacto(CorreoPendiente correo) {
		TextosCorreo.Contacto t = textos.contacto();
		String grave = correo.severidad() == Severidad.GRAVE ? t.grave() : "";
		Map<String, String> valores = Map.of("quien", tieneAlias(correo) ? alias(correo) : t.quienSinAlias(),
				"folio", folio(correo), "grave", grave, "pie", textos.pieContacto(),
				"responder", enlaceRespuesta(correo));
		return mensaje(correo, t.asunto(), t.cuerpo(), valores);
	}

	/**
	 * @return el enlace a la página de respuesta, con el token del aviso después de "#" para que no llegue al
	 *         servidor
	 */
	private String enlaceRespuesta(CorreoPendiente correo) {
		return urlDashboard + "/responder#" + firma.firmar(correo.idIntento());
	}

	/** @return el mensaje con el asunto y el cuerpo rellenos; importante si la alerta es grave */
	private static Mensaje mensaje(CorreoPendiente correo, String asunto, String cuerpo, Map<String, String> valores) {
		return new Mensaje(correo.destinatario(), limpiar(rellenar(asunto, valores)),
				limpiar(rellenar(cuerpo, valores)), correo.severidad() == Severidad.GRAVE);
	}

	/**
	 * @return el texto con cada marcador conocido reemplazado; {Nombre} pone el valor de {nombre} con la
	 *         primera letra en mayúscula. Los desconocidos quedan igual (revisar() los impide al arrancar)
	 */
	private static String rellenar(String texto, Map<String, String> valores) {
		Matcher marcador = MARCADOR.matcher(texto);
		StringBuilder resultado = new StringBuilder();
		while (marcador.find()) {
			String nombre = marcador.group(1);
			String valor = valores.get(minuscula(nombre));
			String reemplazo = valor == null ? marcador.group()
					: Character.isUpperCase(nombre.charAt(0)) ? mayuscula(valor) : valor;
			marcador.appendReplacement(resultado, Matcher.quoteReplacement(reemplazo));
		}
		marcador.appendTail(resultado);
		return resultado.toString();
	}

	/** @return el texto sin espacios en los bordes ni líneas en blanco de más (de un párrafo vacío) */
	private static String limpiar(String texto) {
		return LINEAS_DE_MAS.matcher(texto.strip()).replaceAll("\n\n");
	}

	/** Revisa todos los textos; ver el constructor. */
	private static void revisar(TextosCorreo textos) {
		if (textos == null) {
			throw new IllegalArgumentException("correos.yml: faltan los textos de los correos");
		}
		revisar("pie-equipo", textos.pieEquipo(), Set.of());
		revisar("pie-contacto", textos.pieContacto(), Set.of());
		TextosCorreo.SilencioDatos silencio = requerido("silencio-datos", textos.silencioDatos());
		revisar("silencio-datos.asunto", silencio.asunto(), Set.of("reloj", "folio", "dashboard"));
		revisar("silencio-datos.cuerpo", silencio.cuerpo(), Set.of("reloj", "folio", "dashboard", "pie"));
		revisar("silencio-datos.reloj-con-alias", silencio.relojConAlias(), Set.of("alias"));
		revisar("silencio-datos.reloj-sin-alias", silencio.relojSinAlias(), Set.of());
		TextosCorreo.AlertaEquipo alerta = requerido("alerta-equipo", textos.alertaEquipo());
		revisar("alerta-equipo.asunto", alerta.asunto(), Set.of("alerta", "deQuien", "folio", "dashboard"));
		revisar("alerta-equipo.cuerpo", alerta.cuerpo(), Set.of("alerta", "deQuien", "folio", "dashboard", "pie"));
		revisar("alerta-equipo.alerta-salud", alerta.alertaSalud(), Set.of("gravedad", "folio"));
		revisar("alerta-equipo.alerta-tecnica", alerta.alertaTecnica(), Set.of("tecnica", "folio"));
		revisar("alerta-equipo.de-quien-con-alias", alerta.deQuienConAlias(), Set.of("alias"));
		TextosCorreo.Contacto contacto = requerido("contacto", textos.contacto());
		revisar("contacto.asunto", contacto.asunto(), Set.of("quien", "folio"));
		revisar("contacto.cuerpo", contacto.cuerpo(), Set.of("quien", "folio", "grave", "pie", "responder"));
		exigir("contacto.cuerpo", contacto.cuerpo(), ENLACE);
		revisar("contacto.quien-sin-alias", contacto.quienSinAlias(), Set.of());
		revisar("contacto.grave", contacto.grave(), Set.of());
		for (Severidad severidad : Severidad.values()) {
			if (textos.gravedades() == null || vacio(textos.gravedades().get(severidad))) {
				throw new IllegalArgumentException("correos.yml: falta el nombre de la gravedad " + severidad
						+ " en gravedades");
			}
		}
		Set<TipoEvento> tecnicas = EnumSet.copyOf(TipoEvento.tecnicas());
		tecnicas.remove(TipoEvento.SILENCIO_DATOS);
		for (TipoEvento tipo : tecnicas) {
			if (textos.tecnicas() == null || vacio(textos.tecnicas().get(tipo))) {
				throw new IllegalArgumentException("correos.yml: falta el nombre de la alerta técnica " + tipo
						+ " en tecnicas");
			}
		}
	}

	/** Revisa que el texto exista y que solo use los marcadores permitidos en él. */
	private static void revisar(String donde, String texto, Set<String> permitidos) {
		if (vacio(texto)) {
			throw new IllegalArgumentException("correos.yml: falta el texto " + donde);
		}
		Matcher marcador = MARCADOR.matcher(texto);
		while (marcador.find()) {
			if (!permitidos.contains(minuscula(marcador.group(1)))) {
				throw new IllegalArgumentException("correos.yml: el texto " + donde + " usa " + marcador.group()
						+ ", que no existe ahí. Se puede usar: "
						+ (permitidos.isEmpty() ? "ningún marcador" : "{" + String.join("}, {", permitidos) + "}"));
			}
		}
	}

	/** Revisa que el texto use cada uno de esos marcadores, en orden alfabético para un mensaje estable. */
	private static void exigir(String donde, String texto, Set<String> obligatorios) {
		Set<String> usados = new HashSet<>();
		Matcher marcador = MARCADOR.matcher(texto);
		while (marcador.find()) {
			usados.add(minuscula(marcador.group(1)));
		}
		obligatorios.stream().sorted().filter(m -> !usados.contains(m)).findFirst().ifPresent(falta -> {
			throw new IllegalArgumentException("correos.yml: al texto " + donde + " le falta {" + falta + "}");
		});
	}

	/** @return la parte, o un error si falta en el archivo */
	private static <T> T requerido(String donde, T parte) {
		if (parte == null) {
			throw new IllegalArgumentException("correos.yml: falta la plantilla " + donde);
		}
		return parte;
	}

	/** @return si el texto falta o está en blanco */
	private static boolean vacio(String texto) {
		return texto == null || texto.isBlank();
	}

	/** @return si el paciente tiene un alias usable */
	private static boolean tieneAlias(CorreoPendiente correo) {
		return !vacio(correo.alias());
	}

	/** @return el alias sin espacios en los bordes */
	private static String alias(CorreoPendiente correo) {
		return correo.alias().strip();
	}

	/** @return el folio legible de la alerta, ej. #142 */
	private static String folio(CorreoPendiente correo) {
		return "#" + correo.numero();
	}

	/** @return el texto con la primera letra en mayúscula */
	private static String mayuscula(String texto) {
		return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
	}

	/** @return el texto con la primera letra en minúscula */
	private static String minuscula(String texto) {
		return Character.toLowerCase(texto.charAt(0)) + texto.substring(1);
	}

}
