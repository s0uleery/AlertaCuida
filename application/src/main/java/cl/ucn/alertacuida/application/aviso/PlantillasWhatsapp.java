package cl.ucn.alertacuida.application.aviso;

import cl.ucn.alertacuida.application.aviso.modelo.MensajeWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.PlantillaWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.WhatsappPendiente;
import cl.ucn.alertacuida.application.respuesta.modelo.RespuestaContacto;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Elige la plantilla aprobada de Meta para cada WhatsApp y la rellena (decisión del 24-11-2026): el simulacro
 * usa la de prueba, una alerta grave la que pide llamar al 131 y el resto la leve. Los valores no llevan datos
 * clínicos: solo a quién ({@code quien}: el alias, o un texto si no tiene), el folio y la gravedad. Los botones
 * llevan como payload la respuesta, que Meta devuelve en el webhook al presionarlos.
 */
public class PlantillasWhatsapp {

	/** Lo que puede ir en una variable de la plantilla. */
	static final Set<String> VARIABLES = Set.of("quien", "folio", "gravedad");

	private final PlantillaWhatsapp leve;
	private final PlantillaWhatsapp grave;
	private final PlantillaWhatsapp simulacro;
	private final String quienSinAlias;

	/**
	 * @param leve          para las alertas leves y medias
	 * @param grave         para las graves (pide llamar al 131)
	 * @param simulacro     para los simulacros ("PRUEBA")
	 * @param quienSinAlias valor de {@code quien} si el paciente no tiene alias
	 * @throws IllegalArgumentException si a una plantilla le falta el nombre o el idioma, o usa una variable o un
	 *                                  botón que no existe: así el error aparece al arrancar y no al enviar
	 */
	public PlantillasWhatsapp(PlantillaWhatsapp leve, PlantillaWhatsapp grave, PlantillaWhatsapp simulacro,
			String quienSinAlias) {
		this.leve = revisar("leve", leve);
		this.grave = revisar("grave", grave);
		this.simulacro = revisar("simulacro", simulacro);
		if (quienSinAlias == null || quienSinAlias.isBlank()) {
			throw new IllegalArgumentException(
					"Falta el texto de quien sin alias (alertacuida.whatsapp.quien-sin-alias)");
		}
		this.quienSinAlias = quienSinAlias.strip();
	}

	/**
	 * @param aviso el WhatsApp que toca enviar
	 * @return la plantilla que le corresponde, rellenada
	 */
	public MensajeWhatsapp armar(WhatsappPendiente aviso) {
		PlantillaWhatsapp plantilla = elegir(aviso);
		List<String> valores = new ArrayList<>();
		for (String variable : plantilla.variables()) {
			valores.add(valor(variable, aviso));
		}
		return new MensajeWhatsapp(aviso.telefono(), plantilla.nombre(), plantilla.idioma(), valores,
				plantilla.botones());
	}

	/** @return la plantilla de ese aviso */
	private PlantillaWhatsapp elegir(WhatsappPendiente aviso) {
		if (aviso.esSimulacro()) {
			return simulacro;
		}
		return aviso.severidad() == Severidad.GRAVE ? grave : leve;
	}

	/** @return el texto de esa variable para ese aviso */
	private String valor(String variable, WhatsappPendiente aviso) {
		return switch (variable) {
			case "quien" -> aviso.alias() == null || aviso.alias().isBlank() ? quienSinAlias : aviso.alias().strip();
			case "folio" -> "#" + aviso.numero();
			case "gravedad" -> aviso.severidad().name().toLowerCase(Locale.ROOT);
			default -> throw new IllegalStateException("Variable no revisada: " + variable);
		};
	}

	/** @return la plantilla, si está completa y solo usa variables y botones que existen */
	private static PlantillaWhatsapp revisar(String cual, PlantillaWhatsapp plantilla) {
		Objects.requireNonNull(plantilla, "Falta la plantilla " + cual);
		if (plantilla.nombre() == null || plantilla.nombre().isBlank()) {
			throw new IllegalArgumentException("Falta el nombre de la plantilla " + cual
					+ " (alertacuida.whatsapp.plantillas." + cual + ".nombre)");
		}
		if (plantilla.idioma() == null || plantilla.idioma().isBlank()) {
			throw new IllegalArgumentException("Falta el idioma de la plantilla " + cual);
		}
		for (String variable : plantilla.variables()) {
			if (!VARIABLES.contains(variable)) {
				throw new IllegalArgumentException("La plantilla " + cual + " usa la variable " + variable
						+ ", que no existe. Se puede usar: " + String.join(", ", VARIABLES));
			}
		}
		for (String boton : plantilla.botones()) {
			try {
				RespuestaContacto.valueOf(boton);
			} catch (IllegalArgumentException desconocido) {
				throw new IllegalArgumentException("La plantilla " + cual + " tiene el botón " + boton
						+ ", que no existe. Se puede usar: VOY_EN_CAMINO, NO_PUEDO");
			}
		}
		return plantilla;
	}

}
