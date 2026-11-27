package cl.ucn.alertacuida.application.aviso;

import cl.ucn.alertacuida.application.aviso.modelo.AccionWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.BotonInteractivo;
import cl.ucn.alertacuida.application.aviso.modelo.ConversacionWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.EtapaConversacion;
import cl.ucn.alertacuida.application.aviso.modelo.EventoWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.NivelConversacion;
import cl.ucn.alertacuida.application.aviso.modelo.PoliticaConversacionWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.ResultadoCierreWhatsapp;
import cl.ucn.alertacuida.application.aviso.puerto.ConversacionesWhatsapp;
import cl.ucn.alertacuida.application.respuesta.modelo.RespuestaContacto;
import cl.ucn.alertacuida.domain.alerta.EstadoContacto;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Máquina de estados del flujo conversacional de WhatsApp Business (Flujo de mensajes Alertas v3):
 * <ul>
 * <li><b>Nivel 1 (PRLAC) y Nivel 2 (Tercero significativo):</b> Aceptar llamada (10 min de límite) →
 * ¿Cómo está la persona? → Disponibilidad en terreno → Confirmación de llegada con recordatorios →
 * Resultado en terreno → Cierre parcial pendiente de validación PRLAC.</li>
 * <li><b>Nivel 3 (Servicios públicos) y Simulacro:</b> Despacho directo sin llamada previa (Atenderemos /
 * Atender simulacro) → Confirmación de llegada con recordatorios → Resultado en terreno → Cierre parcial.</li>
 * <li>Si otra persona ya tomó o cerró la alerta, se avisa al contacto al intentar tomarla dentro de su ventana
 * de 24 h (sin consumir una nueva plantilla).</li>
 * </ul>
 */
public class GestionarConversacionWhatsapp {

	private static final Duration VENTANA_24H = Duration.ofHours(24);
	private static final System.Logger LOG = System.getLogger(GestionarConversacionWhatsapp.class.getName());

	private final ConversacionesWhatsapp conversaciones;
	private final PoliticaConversacionWhatsapp politica;
	private final Clock reloj;

	/**
	 * @param conversaciones persistencia de conversaciones y bandeja saliente
	 * @param politica       plazos de llamada y recordatorios por severidad
	 * @param reloj          hora del servidor
	 */
	public GestionarConversacionWhatsapp(ConversacionesWhatsapp conversaciones, PoliticaConversacionWhatsapp politica,
			Clock reloj) {
		this.conversaciones = conversaciones;
		this.politica = politica;
		this.reloj = reloj;
	}

	/**
	 * Procesa un botón presionado si pertenece a una conversación registrada.
	 *
	 * @param boton evento leído del webhook
	 * @return true si el evento fue reconocido y manejado por el motor conversacional (incluyendo duplicados);
	 *         false si no existe conversación para ese wamid (permite respaldo heredado por registro_intento)
	 */
	public boolean procesarBoton(EventoWhatsapp.Boton boton) {
		Instant ahora = reloj.instant();
		if (boton.idMensajeEntrante() != null && !boton.idMensajeEntrante().isBlank()
				&& !conversaciones.registrarMensajeProcesado(boton.idMensajeEntrante(), ahora)) {
			return true;
		}
		if (boton.idMensajeOriginal() == null || boton.idMensajeOriginal().isBlank()) {
			return false;
		}
		Optional<ConversacionWhatsapp> encontrada = conversaciones.buscarPorWamid(boton.idMensajeOriginal());
		if (encontrada.isEmpty()) {
			return false;
		}
		Optional<AccionWhatsapp> accion = AccionWhatsapp.desde(boton.valor());
		if (accion.isEmpty()) {
			LOG.log(System.Logger.Level.INFO, "Botón desconocido ignorado en conversación de WhatsApp");
			return true;
		}
		aplicar(encontrada.get(), accion.get(), ahora);
		return true;
	}

	/** Aplica la transición de la máquina de estados según la etapa actual y la acción recibida. */
	private void aplicar(ConversacionWhatsapp conv, AccionWhatsapp accion, Instant ahora) {
		Instant ventana = ahora.plus(VENTANA_24H);
		if (alertaCerrada(conv.estadoAlerta())) {
			if (!conv.etapa().esTerminal()) {
				conversaciones.cerrarConversacion(conv.idConversacion(), EtapaConversacion.DESCARTADA, ahora);
			}
			encolarTexto(conv, "La alerta #" + conv.numeroAlerta() + " ya fue cerrada. Gracias por su respuesta.",
					ahora);
			return;
		}
		if (conv.etapa().esTerminal()) {
			encolarTexto(conv, "Esta interacción de la alerta #" + conv.numeroAlerta() + " ya finalizó. Gracias.",
					ahora);
			return;
		}
		switch (conv.etapa()) {
			case ESPERANDO_ACEPTACION -> manejarAceptacion(conv, accion, ventana, ahora);
			case ESPERANDO_ESTADO_LLAMADA -> manejarEstadoLlamada(conv, accion, ventana, ahora);
			case ESPERANDO_DISPONIBILIDAD -> manejarDisponibilidad(conv, accion, ventana, ahora);
			case ESPERANDO_LLEGADA -> manejarLlegada(conv, accion, ventana, ahora);
			case ESPERANDO_RESULTADO_VISITA -> manejarResultadoVisita(conv, accion, ahora);
			default -> {
				// Las etapas terminales ya se manejaron arriba
			}
		}
	}

	/** Etapa 1: respuesta a la plantilla inicial (tomar llamada / atender directo / no disponible). */
	private void manejarAceptacion(ConversacionWhatsapp conv, AccionWhatsapp accion, Instant ventana, Instant ahora) {
		if (accion == AccionWhatsapp.NO_DISPONIBLE || accion == AccionWhatsapp.NO_PUEDO) {
			conversaciones.cerrarConversacion(conv.idConversacion(), EtapaConversacion.RECHAZADA, ahora);
			conversaciones.registrarRespuestaIntento(conv.idIntento(), RespuestaContacto.NO_PUEDO, ahora);
			notificarCambioEstadoReloj(conv);
			encolarTexto(conv, "Entendido. Escalaremos la alerta #" + conv.numeroAlerta()
					+ " al siguiente contacto. Gracias por avisar.", ahora);
			return;
		}
		boolean despachoDirecto = conv.nivel() == NivelConversacion.NIVEL_3_SERVICIO
				|| conv.nivel() == NivelConversacion.SIMULACRO
				|| accion == AccionWhatsapp.ATENDEREMOS
				|| accion == AccionWhatsapp.ATENDER_SIMULACRO;
		if (!despachoDirecto && accion != AccionWhatsapp.TOMAR_LLAMADA && accion != AccionWhatsapp.VOY_EN_CAMINO) {
			return;
		}
		if (conv.estadoAlerta() == EstadoContacto.CONFIRMADA) {
			conversaciones.cerrarConversacion(conv.idConversacion(), EtapaConversacion.DESCARTADA, ahora);
			encolarTexto(conv, "La alerta #" + conv.numeroAlerta()
					+ " ya fue tomada por otro contacto. Gracias por su disposición.", ahora);
			return;
		}
		EtapaConversacion destino = despachoDirecto ? EtapaConversacion.ESPERANDO_LLEGADA
				: EtapaConversacion.ESPERANDO_ESTADO_LLAMADA;
		Instant vencimiento = despachoDirecto ? ahora.plus(politica.intervaloLlegada(conv.severidad()))
				: ahora.plus(politica.plazoLlamada());
		if (!conversaciones.tomar(conv.idConversacion(), destino, vencimiento, ventana, ahora)) {
			conversaciones.cerrarConversacion(conv.idConversacion(), EtapaConversacion.DESCARTADA, ahora);
			encolarTexto(conv, "La alerta #" + conv.numeroAlerta()
					+ " ya fue tomada por otro contacto. Gracias por su disposición.", ahora);
			return;
		}
		conversaciones.registrarRespuestaIntento(conv.idIntento(), RespuestaContacto.VOY_EN_CAMINO, ahora);
		notificarCambioEstadoReloj(conv);
		if (despachoDirecto) {
			enviarPreguntaLlegada(conv, "Confirmado para la alerta #" + conv.numeroAlerta()
					+ ". Avise cuando el personal llegue al domicilio:", ahora);
		} else {
			enviarPreguntaEstadoLlamada(conv, ahora);
		}
	}

	/** Etapa 2 (Nivel 1 y 2): resultado de la llamada al reloj del paciente. */
	private void manejarEstadoLlamada(ConversacionWhatsapp conv, AccionWhatsapp accion, Instant ventana,
			Instant ahora) {
		if (accion == AccionWhatsapp.LLAMADA_BIEN) {
			cerrarConResultado(conv, ResultadoCierreWhatsapp.FALSA_ALARMA,
					"Gracias. Registramos que la persona se encuentra bien y dejamos la alerta #"
							+ conv.numeroAlerta() + " en cierre parcial para validación de PRLAC.", ahora);
			return;
		}
		if (accion != AccionWhatsapp.LLAMADA_NECESITA_ATENCION && accion != AccionWhatsapp.LLAMADA_NO_CONTESTA) {
			return;
		}
		if (!conversaciones.avanzar(conv.idConversacion(), EtapaConversacion.ESPERANDO_ESTADO_LLAMADA,
				EtapaConversacion.ESPERANDO_DISPONIBILIDAD, ahora.plus(politica.plazoLlamada()), ventana, ahora)) {
			return;
		}
		if (conv.nivel() == NivelConversacion.NIVEL_1_PRLAC) {
			conversaciones.encolarMensajeSaliente(conv.idConversacion(),
					"Alerta #" + conv.numeroAlerta() + ": ¿hay personal disponible en terreno para acudir?",
					List.of(new BotonInteractivo(AccionWhatsapp.PERSONAL_ASIGNADO.name(), "Personal asignado"),
							new BotonInteractivo(AccionWhatsapp.SIN_DISPONIBILIDAD.name(), "Sin disponibilidad")),
					ahora);
		} else {
			conversaciones.encolarMensajeSaliente(conv.idConversacion(),
					"Alerta #" + conv.numeroAlerta() + ": ¿puede acudir al domicilio para asistir a la persona?",
					List.of(new BotonInteractivo(AccionWhatsapp.ATENDERE.name(), "Atenderé"),
							new BotonInteractivo(AccionWhatsapp.NO_PUEDO_ACUDIR.name(), "No puedo")),
					ahora);
		}
	}

	/** Etapa 3 (Nivel 1 y 2): coordinación de traslado al domicilio o escalamiento por falta de disponibilidad. */
	private void manejarDisponibilidad(ConversacionWhatsapp conv, AccionWhatsapp accion, Instant ventana,
			Instant ahora) {
		if (accion == AccionWhatsapp.SIN_DISPONIBILIDAD || accion == AccionWhatsapp.NO_PUEDO_ACUDIR
				|| accion == AccionWhatsapp.NO_DISPONIBLE || accion == AccionWhatsapp.NO_PUEDO) {
			conversaciones.cerrarConversacion(conv.idConversacion(), EtapaConversacion.RECHAZADA, ahora);
			notificarCambioEstadoReloj(conv);
			encolarTexto(conv, "Entendido. Escalaremos la alerta #" + conv.numeroAlerta()
					+ " al siguiente contacto. Gracias por avisar.", ahora);
			return;
		}
		if (accion != AccionWhatsapp.PERSONAL_ASIGNADO && accion != AccionWhatsapp.ATENDERE) {
			return;
		}
		Instant primerRecordatorio = ahora.plus(politica.intervaloLlegada(conv.severidad()));
		if (!conversaciones.avanzar(conv.idConversacion(), EtapaConversacion.ESPERANDO_DISPONIBILIDAD,
				EtapaConversacion.ESPERANDO_LLEGADA, primerRecordatorio, ventana, ahora)) {
			return;
		}
		String texto = conv.nivel() == NivelConversacion.NIVEL_1_PRLAC
				? "Personal asignado para la alerta #" + conv.numeroAlerta()
						+ ". Avise cuando el personal llegue al domicilio:"
				: "Gracias por acudir a la alerta #" + conv.numeroAlerta()
						+ ". Avise cuando llegue al domicilio:";
		enviarPreguntaLlegada(conv, texto, ahora);
	}

	/** Etapa 4 (todos los niveles): confirmación de llegada o acuse de que aún no llega. */
	private void manejarLlegada(ConversacionWhatsapp conv, AccionWhatsapp accion, Instant ventana, Instant ahora) {
		if (accion == AccionWhatsapp.AUN_NO_LLEGA) {
			// El tiempo límite de recordatorios corre independientemente: solo se refresca la ventana de 24 h
			conversaciones.refrescarVentana(conv.idConversacion(), ventana, ahora);
			encolarTexto(conv, "Entendido, seguimos pendientes de la llegada para la alerta #"
					+ conv.numeroAlerta() + ".", ahora);
			return;
		}
		if (accion != AccionWhatsapp.LLEGO) {
			return;
		}
		if (!conversaciones.avanzar(conv.idConversacion(), EtapaConversacion.ESPERANDO_LLEGADA,
				EtapaConversacion.ESPERANDO_RESULTADO_VISITA, null, ventana, ahora)) {
			return;
		}
		conversaciones.registrarLlegadaIntento(conv.idIntento(), ahora);
		enviarPreguntaResultadoVisita(conv, ahora);
	}

	/** Etapa 5 (todos los niveles): resultado observado en terreno y cierre parcial. */
	private void manejarResultadoVisita(ConversacionWhatsapp conv, AccionWhatsapp accion, Instant ahora) {
		switch (accion) {
			case RESULTADO_BIEN -> cerrarConResultado(conv, ResultadoCierreWhatsapp.FALSA_ALARMA,
					"Gracias. Registramos que la persona está bien y dejamos la alerta #" + conv.numeroAlerta()
							+ " en cierre parcial para validación de PRLAC.", ahora);
			case RESULTADO_APOYO_NO_URGENTE -> {
				String nota = conv.nivel() == NivelConversacion.NIVEL_1_PRLAC
						? "Registramos apoyo no urgente (coordinar con CESFAM). La alerta #" + conv.numeroAlerta()
								+ " queda en cierre parcial para validación definitiva en la plataforma."
						: "Registramos apoyo no urgente y notificamos al PRLAC para seguimiento. La alerta #"
								+ conv.numeroAlerta() + " queda en cierre parcial.";
				cerrarConResultado(conv, ResultadoCierreWhatsapp.APOYO_NO_URGENTE, nota, ahora);
			}
			case RESULTADO_EMERGENCIA -> {
				String instruccion = conv.nivel() == NivelConversacion.NIVEL_3_SERVICIO
						|| conv.nivel() == NivelConversacion.SIMULACRO
						? "Registramos emergencia con derivación a SAMU. La alerta #" + conv.numeroAlerta()
								+ " queda en cierre parcial para validación de PRLAC."
						: "Registramos emergencia. Por favor llame al 131 de inmediato si aún no lo ha hecho. "
								+ "La alerta #" + conv.numeroAlerta()
								+ " queda en cierre parcial para validación de PRLAC.";
				cerrarConResultado(conv, ResultadoCierreWhatsapp.EMERGENCIA, instruccion, ahora);
			}
			default -> {
				// Botón de otra etapa ignorado
			}
		}
	}

	/** Pregunta "¿Cómo está la persona?" tras pulsar "Llamaré al paciente". */
	private void enviarPreguntaEstadoLlamada(ConversacionWhatsapp conv, Instant ahora) {
		String telefono = conv.telefonoReloj() == null || conv.telefonoReloj().isBlank() ? ""
				: " Llame al reloj: " + conv.telefonoReloj() + ".";
		String cuerpo = "Alerta #" + conv.numeroAlerta() + " tomada." + telefono
				+ " ¿Cómo está la persona? (Tiene " + politica.plazoLlamada().toMinutes()
				+ " minutos para responder).";
		conversaciones.encolarMensajeSaliente(conv.idConversacion(), cuerpo,
				List.of(new BotonInteractivo(AccionWhatsapp.LLAMADA_BIEN.name(), "Está bien"),
						new BotonInteractivo(AccionWhatsapp.LLAMADA_NECESITA_ATENCION.name(), "Necesita atención"),
						new BotonInteractivo(AccionWhatsapp.LLAMADA_NO_CONTESTA.name(), "No contesta")),
				ahora);
	}

	/** Envía los botones de confirmación de llegada adaptados al nivel. */
	static List<BotonInteractivo> botonesLlegada(NivelConversacion nivel) {
		if (nivel == NivelConversacion.NIVEL_2_TERCERO) {
			return List.of(new BotonInteractivo(AccionWhatsapp.LLEGO.name(), "Llegué"),
					new BotonInteractivo(AccionWhatsapp.AUN_NO_LLEGA.name(), "Aún no llego"));
		}
		return List.of(new BotonInteractivo(AccionWhatsapp.LLEGO.name(), "Ya llegó"),
				new BotonInteractivo(AccionWhatsapp.AUN_NO_LLEGA.name(), "Aún no llega"));
	}

	/** Encola la pregunta de confirmación de llegada. */
	private void enviarPreguntaLlegada(ConversacionWhatsapp conv, String cuerpo, Instant ahora) {
		conversaciones.encolarMensajeSaliente(conv.idConversacion(), cuerpo, botonesLlegada(conv.nivel()), ahora);
	}

	/** Encola la pregunta de resultado observado en terreno (títulos ≤ 20 caracteres). */
	private void enviarPreguntaResultadoVisita(ConversacionWhatsapp conv, Instant ahora) {
		String bien;
		String emergencia;
		String cuerpo;
		if (conv.nivel() == NivelConversacion.NIVEL_1_PRLAC) {
			bien = "Persona bien";
			emergencia = "Emergencia (131)";
			cuerpo = "Llegada registrada para la alerta #" + conv.numeroAlerta()
					+ ". ¿Cómo encontró a la persona?\n- Apoyo no urgente: coordina con CESFAM."
					+ "\n- Emergencia: llamar al 131.";
		} else if (conv.nivel() == NivelConversacion.NIVEL_2_TERCERO) {
			bien = "Está bien";
			emergencia = "Emergencia (131)";
			cuerpo = "Llegada registrada para la alerta #" + conv.numeroAlerta()
					+ ". ¿Cómo encontró a la persona?\n- Apoyo no urgente: se avisará al PRLAC para seguimiento."
					+ "\n- Emergencia: llamar al 131.";
		} else {
			bien = "Persona bien";
			emergencia = "Emergencia (SAMU)";
			cuerpo = "Llegada registrada para la alerta #" + conv.numeroAlerta()
					+ ". Registre el estado de la persona:\n- Apoyo no urgente: se avisará al PRLAC."
					+ "\n- Emergencia: derivar a SAMU.";
		}
		conversaciones.encolarMensajeSaliente(conv.idConversacion(), cuerpo,
				List.of(new BotonInteractivo(AccionWhatsapp.RESULTADO_BIEN.name(), bien),
						new BotonInteractivo(AccionWhatsapp.RESULTADO_APOYO_NO_URGENTE.name(), "Apoyo no urgente"),
						new BotonInteractivo(AccionWhatsapp.RESULTADO_EMERGENCIA.name(), emergencia)),
				ahora);
	}

	/** Completa la conversación, registra el cierre parcial y notifica al reloj. */
	private void cerrarConResultado(ConversacionWhatsapp conv, ResultadoCierreWhatsapp resultado, String mensajeCierre,
			Instant ahora) {
		conversaciones.cerrarConversacion(conv.idConversacion(), EtapaConversacion.COMPLETADA, ahora);
		conversaciones.cerrarAlertaParcial(conv.idAlerta(), resultado, ahora);
		notificarCambioEstadoReloj(conv);
		encolarTexto(conv, mensajeCierre, ahora);
	}

	/** Encola un aviso FCM CAMBIO_ESTADO_ALERTA si la alerta proviene del reloj. */
	private void notificarCambioEstadoReloj(ConversacionWhatsapp conv) {
		if (conv.delReloj()) {
			conversaciones.dejarAvisoCambioEstadoReloj(conv.idAlerta());
		}
	}

	/** Encola una respuesta de texto simple dentro de la ventana de 24 h. */
	private void encolarTexto(ConversacionWhatsapp conv, String cuerpo, Instant ahora) {
		conversaciones.encolarMensajeSaliente(conv.idConversacion(), cuerpo, List.of(), ahora);
	}

	/** @return si la alerta ya terminó por cierre o cancelación */
	private static boolean alertaCerrada(EstadoContacto estado) {
		return estado == EstadoContacto.RESUELTA || estado == EstadoContacto.CANCELADA;
	}

}
