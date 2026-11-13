package cl.ucn.alertacuida.application.soporte;

import cl.ucn.alertacuida.application.aviso.modelo.TextosCorreo;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.util.Map;

/** Textos de correo cortos para las pruebas, con todos los marcadores. Los reales están en correos.yml. */
public final class TextosCorreoDePrueba {

	private TextosCorreoDePrueba() {
	}

	/** @return los textos de prueba */
	public static TextosCorreo textos() {
		return new TextosCorreo("Pie del equipo.", "Pie del contacto.",
				new TextosCorreo.SilencioDatos("Silencio: {reloj} ({folio})",
						"{Reloj} dejó de enviar datos.\n\nVer: {dashboard}\n\n{pie}\n", "el reloj de {alias}",
						"un reloj"),
				new TextosCorreo.AlertaEquipo("Alerta: {alerta}{deQuien}",
						"Hay una {alerta}{deQuien}.\n\nVer: {dashboard}\n\n{pie}\n", "alerta {gravedad} {folio}",
						"alerta técnica {folio} ({tecnica})", " de {alias}"),
				new TextosCorreo.Contacto("Ayuda: {quien} ({folio})",
						"Alerta de {quien}.\n\n{grave}\n\nResponder: {responder}\n\n{pie}\n",
						"la persona a su cuidado", "Llame al 131."),
				new TextosCorreo.Vigilante("Vigilante: {alerta}{deQuien} no avanza",
						"La {alerta}{deQuien} no avanza hace {minutos} minutos.\n\nVer: {dashboard}\n\n{pie}\n"),
				Map.of(Severidad.LEVE, "leve", Severidad.MEDIA, "media", Severidad.GRAVE, "grave"),
				Map.of(TipoEvento.BATERIA_BAJA, "batería baja", TipoEvento.RELOJ_NO_PUESTO, "reloj no puesto",
						TipoEvento.EXCESO_ALERTAS, "exceso de alertas"));
	}

}
