package cl.ucn.alertacuida.application.aviso;

import cl.ucn.alertacuida.application.aviso.modelo.CorreoPendiente;
import cl.ucn.alertacuida.application.aviso.modelo.Mensaje;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.util.Map;

/**
 * Plantillas mínimas del correo institucional, sin datos clínicos: del paciente solo va su alias (o,
 * si no tiene, el folio de la alerta), y de la alerta, su folio y su gravedad. El tipo de una alerta
 * clínica no se nombra; el de una técnica sí, porque habla del reloj y no de la persona.
 * <ul>
 * <li>Silencio de datos, al equipo.</li>
 * <li>Alerta, al equipo: las graves y el respaldo de un WhatsApp a la oficina.</li>
 * <li>Aviso a un contacto del paciente (respaldo de su WhatsApp): en las graves, le indica llamar al 131.</li>
 * </ul>
 */
public class PlantillasCorreo {

	/** Nombre legible de las alertas técnicas, que hablan del reloj. */
	private static final Map<TipoEvento, String> TECNICAS = Map.of(
			TipoEvento.BATERIA_BAJA, "batería baja",
			TipoEvento.RELOJ_NO_PUESTO, "reloj no puesto",
			TipoEvento.EXCESO_ALERTAS, "exceso de alertas");

	private static final String PIE_EQUIPO = "Este correo no incluye datos clínicos. Lo envía AlertaCuida"
			+ " automáticamente: no lo responda.";
	private static final String PIE_CONTACTO = "Este correo no incluye datos de salud. Lo envía AlertaCuida, de la"
			+ " oficina PRLAC, automáticamente: no lo responda.";

	private final String urlDashboard;

	/** @param urlDashboard dirección del dashboard, para que el equipo abra la alerta */
	public PlantillasCorreo(String urlDashboard) {
		this.urlDashboard = urlDashboard;
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
		String reloj = tieneAlias(correo) ? "el reloj de " + correo.alias().strip() : "un reloj";
		return new Mensaje(correo.destinatario(),
				"AlertaCuida: " + reloj + " dejó de sincronizar (" + folio(correo) + ")",
				mayuscula(reloj) + " dejó de enviar datos al servidor (alerta " + folio(correo) + "). Puede estar"
						+ " apagado, sin batería o sin señal.\n\n"
						+ "Revise la alerta en el dashboard: " + urlDashboard + "\n\n" + PIE_EQUIPO);
	}

	/** Una alerta grave, o el respaldo de un WhatsApp a la oficina por cualquier alerta. */
	private Mensaje alertaAlEquipo(CorreoPendiente correo) {
		String tecnica = TECNICAS.get(correo.tipo());
		String alerta = tecnica == null
				? "alerta " + gravedad(correo.severidad()) + " " + folio(correo)
				: "alerta técnica " + folio(correo) + " (" + tecnica + ")";
		String deQuien = tieneAlias(correo) ? " de " + correo.alias().strip() : "";
		return new Mensaje(correo.destinatario(),
				"AlertaCuida: " + alerta + deQuien,
				"Hay una " + alerta + deQuien + " que requiere atención.\n\n"
						+ "Revísela en el dashboard: " + urlDashboard + "\n\n" + PIE_EQUIPO);
	}

	/** Aviso a un contacto del paciente: comunicarse con la persona y, si es grave y no puede, llamar al 131. */
	private static Mensaje aContacto(CorreoPendiente correo) {
		String quien = tieneAlias(correo) ? correo.alias().strip() : "la persona a su cuidado";
		String asunto = tieneAlias(correo)
				? "AlertaCuida: " + quien + " necesita ayuda (alerta " + folio(correo) + ")"
				: "AlertaCuida: alerta " + folio(correo) + " de la persona a su cuidado";
		String grave = correo.severidad() == Severidad.GRAVE
				? "Es una alerta grave: si no logra comunicarse, llame al SAMU (131).\n\n"
				: "";
		return new Mensaje(correo.destinatario(), asunto,
				"Recibimos una alerta de " + quien + " (alerta " + folio(correo) + ").\n\n"
						+ "Por favor, comuníquese con " + quien + " o vaya a verle lo antes posible.\n\n"
						+ grave + PIE_CONTACTO);
	}

	/** @return si el paciente tiene un alias usable */
	private static boolean tieneAlias(CorreoPendiente correo) {
		return correo.alias() != null && !correo.alias().isBlank();
	}

	/** @return el folio legible de la alerta, ej. #142 */
	private static String folio(CorreoPendiente correo) {
		return "#" + correo.numero();
	}

	/** @return la gravedad en palabras */
	private static String gravedad(Severidad severidad) {
		return switch (severidad) {
			case LEVE -> "leve";
			case MEDIA -> "media";
			case GRAVE -> "grave";
		};
	}

	/** @return el texto con la primera letra en mayúscula */
	private static String mayuscula(String texto) {
		return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
	}

}
