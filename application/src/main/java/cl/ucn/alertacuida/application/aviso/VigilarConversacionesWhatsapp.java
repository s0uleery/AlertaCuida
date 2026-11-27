package cl.ucn.alertacuida.application.aviso;

import cl.ucn.alertacuida.application.aviso.modelo.ConversacionWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.EtapaConversacion;
import cl.ucn.alertacuida.application.aviso.modelo.PoliticaConversacionWhatsapp;
import cl.ucn.alertacuida.application.aviso.puerto.ConversacionesWhatsapp;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.domain.alerta.EstadoContacto;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Revisa periódicamente los plazos de las conversaciones activas de WhatsApp (decisiones del 26-11-2026):
 * <ul>
 * <li><b>Llamada (10 min):</b> si quien indicó "Llamaré al paciente" no responde en el plazo, expira la
 * conversación y notifica al reloj ({@code CAMBIO_ESTADO_ALERTA}) para escalar al siguiente contacto.</li>
 * <li><b>Recordatorios de llegada (independientes):</b> LEVE cada 30 min (5 intervalos = 2 h 30 min),
 * MEDIA cada 15 min (5 intervalos = 1 h 15 min), GRAVE cada 5 min (8 intervalos = 40 min). Se repiten
 * incluso si el anterior fue ignorado; al agotarse el límite, expira la conversación y avisa al reloj para
 * escalar.</li>
 * </ul>
 */
public class VigilarConversacionesWhatsapp {

	private static final System.Logger LOG = System.getLogger(VigilarConversacionesWhatsapp.class.getName());

	private final ConversacionesWhatsapp conversaciones;
	private final PoliticaConversacionWhatsapp politica;
	private final Transacciones transacciones;
	private final Clock reloj;

	/**
	 * @param conversaciones persistencia de conversaciones
	 * @param politica       intervalos y límites por severidad
	 */
	public VigilarConversacionesWhatsapp(ConversacionesWhatsapp conversaciones, PoliticaConversacionWhatsapp politica,
			Transacciones transacciones, Clock reloj) {
		this.conversaciones = conversaciones;
		this.politica = politica;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/** Revisa todas las conversaciones vencidas en este instante; una falla no detiene a las demás. */
	public void vigilar() {
		Instant ahora = reloj.instant();
		for (ConversacionWhatsapp conv : conversaciones.vencidas(ahora)) {
			try {
				transacciones.ejecutar(() -> {
					procesar(conv, ahora);
					return null;
				});
			} catch (RuntimeException falla) {
				LOG.log(System.Logger.Level.ERROR, "Error al vigilar conversación de WhatsApp " + conv.idConversacion(),
						falla);
			}
		}
	}

	/** Aplica el vencimiento correspondiente a una conversación. */
	private void procesar(ConversacionWhatsapp conv, Instant ahora) {
		if (conv.estadoAlerta() == EstadoContacto.RESUELTA || conv.estadoAlerta() == EstadoContacto.CANCELADA) {
			conversaciones.cerrarConversacion(conv.idConversacion(), EtapaConversacion.DESCARTADA, ahora);
			return;
		}
		if (conv.etapa() == EtapaConversacion.ESPERANDO_ESTADO_LLAMADA
				|| conv.etapa() == EtapaConversacion.ESPERANDO_DISPONIBILIDAD) {
			expirarYEscalar(conv, "Se cumplió el plazo de espera para la alerta #" + conv.numeroAlerta()
					+ ". Escalaremos al siguiente contacto.", ahora);
			return;
		}
		if (conv.etapa() == EtapaConversacion.ESPERANDO_LLEGADA) {
			procesarRecordatorioLlegada(conv, ahora);
		}
	}

	/** Envía el siguiente recordatorio de llegada o agota la paciencia y escala al siguiente contacto. */
	private void procesarRecordatorioLlegada(ConversacionWhatsapp conv, Instant ahora) {
		int enviados = conv.recordatoriosEnviados();
		int maximo = politica.maximoRecordatorios(conv.severidad());
		if (enviados + 1 >= maximo) {
			expirarYEscalar(conv, "No se confirmó llegada para la alerta #" + conv.numeroAlerta()
					+ " dentro del tiempo límite. Escalaremos al siguiente contacto.", ahora);
			return;
		}
		Duration intervalo = politica.intervaloLlegada(conv.severidad());
		int siguiente = enviados + 1;
		conversaciones.registrarRecordatorio(conv.idConversacion(), siguiente, ahora.plus(intervalo), ahora);
		if (conv.ventanaAbierta(ahora)) {
			String cuerpo = "Recordatorio (" + siguiente + "/" + (maximo - 1) + ") alerta #" + conv.numeroAlerta()
					+ ": ¿ya llegó al domicilio?";
			conversaciones.encolarMensajeSaliente(conv.idConversacion(), cuerpo,
					GestionarConversacionWhatsapp.botonesLlegada(conv.nivel()), ahora);
		}
	}

	/** Expira la conversación, avisa al contacto si su ventana de 24 h sigue abierta y notifica al reloj. */
	private void expirarYEscalar(ConversacionWhatsapp conv, String avisoContacto, Instant ahora) {
		conversaciones.cerrarConversacion(conv.idConversacion(), EtapaConversacion.EXPIRADA, ahora);
		if (conv.delReloj()) {
			conversaciones.dejarAvisoCambioEstadoReloj(conv.idAlerta());
		}
		if (conv.ventanaAbierta(ahora)) {
			conversaciones.encolarMensajeSaliente(conv.idConversacion(), avisoContacto, List.of(), ahora);
		}
	}

}
