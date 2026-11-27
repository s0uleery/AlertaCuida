package cl.ucn.alertacuida.application.aviso;

import cl.ucn.alertacuida.application.aviso.error.EnvioRechazado;
import cl.ucn.alertacuida.application.aviso.modelo.AccionWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.MensajeWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.NivelConversacion;
import cl.ucn.alertacuida.application.aviso.modelo.ParametroPlantilla;
import cl.ucn.alertacuida.application.aviso.modelo.PlantillaWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.UbicacionWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.WhatsappPendiente;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Elige la plantilla aprobada de Meta según el nivel del contacto y rellena sus parámetros ({@code NAMED} o
 * {@code POSITIONAL}) y su ubicación ({@code HEADER: LOCATION}), con respaldo automático a una plantilla sin mapa
 * cuando la alerta no trae coordenadas GPS (decisiones del 26-11-2026).
 */
public class PlantillasWhatsapp {

	/** Variables reconocidas para las plantillas de Meta. */
	static final Set<String> VARIABLES = Set.of("id_alerta", "alias_paciente", "nombre_paciente", "edad",
			"rut_paciente", "tipo_evento", "gravedad", "hora_evento", "nota_adicional", "numero_reloj",
			"quien", "folio");

	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

	private final PlantillaWhatsapp nivel1Y2;
	private final PlantillaWhatsapp nivel1Y2SinMapa;
	private final PlantillaWhatsapp nivel3Edad;
	private final PlantillaWhatsapp nivel3EdadSinMapa;
	private final PlantillaWhatsapp nivel3Rut;
	private final PlantillaWhatsapp nivel3RutSinMapa;
	private final PlantillaWhatsapp simulacro;
	private final PlantillaWhatsapp simulacroSinMapa;
	private final boolean usarPlantillaRutCarabineros;
	private final String quienSinAlias;

	/**
	 * @param nivel1Y2                    plantilla principal para PRLAC y terceros significativos
	 * @param nivel1Y2SinMapa             respaldo sin mapa si falta GPS (opcional)
	 * @param nivel3Edad                  plantilla de Nivel 3 con edad (y por defecto para Carabineros)
	 * @param nivel3EdadSinMapa           respaldo sin mapa de Nivel 3 edad (opcional)
	 * @param nivel3Rut                   plantilla de Nivel 3 con RUT para Carabineros (opcional)
	 * @param nivel3RutSinMapa            respaldo sin mapa de Nivel 3 RUT (opcional)
	 * @param simulacro                   plantilla de simulacro
	 * @param simulacroSinMapa            respaldo sin mapa de simulacro (opcional)
	 * @param usarPlantillaRutCarabineros si se usa la plantilla con RUT para tipo CARABINEROS
	 * @param quienSinAlias               texto de respaldo cuando el paciente no tiene alias
	 */
	public PlantillasWhatsapp(PlantillaWhatsapp nivel1Y2, PlantillaWhatsapp nivel1Y2SinMapa,
			PlantillaWhatsapp nivel3Edad, PlantillaWhatsapp nivel3EdadSinMapa, PlantillaWhatsapp nivel3Rut,
			PlantillaWhatsapp nivel3RutSinMapa, PlantillaWhatsapp simulacro, PlantillaWhatsapp simulacroSinMapa,
			boolean usarPlantillaRutCarabineros, String quienSinAlias) {
		this.nivel1Y2 = revisarObligatoria("nivel-1-y-2", nivel1Y2);
		this.nivel1Y2SinMapa = revisarOpcional("nivel-1-y-2-sin-mapa", nivel1Y2SinMapa);
		this.nivel3Edad = revisarObligatoria("nivel-3-edad", nivel3Edad);
		this.nivel3EdadSinMapa = revisarOpcional("nivel-3-edad-sin-mapa", nivel3EdadSinMapa);
		this.nivel3Rut = revisarOpcional("nivel-3-rut", nivel3Rut);
		this.nivel3RutSinMapa = revisarOpcional("nivel-3-rut-sin-mapa", nivel3RutSinMapa);
		this.simulacro = revisarObligatoria("simulacro", simulacro);
		this.simulacroSinMapa = revisarOpcional("simulacro-sin-mapa", simulacroSinMapa);
		this.usarPlantillaRutCarabineros = usarPlantillaRutCarabineros;
		if (quienSinAlias == null || quienSinAlias.isBlank()) {
			throw new IllegalArgumentException(
					"Falta el texto de quien sin alias (alertacuida.whatsapp.quien-sin-alias)");
		}
		this.quienSinAlias = quienSinAlias.strip();
	}

	/**
	 * @param aviso el WhatsApp que toca enviar
	 * @return la plantilla lista para enviar por la Cloud API
	 * @throws EnvioRechazado si la plantilla exige ubicación GPS, la alerta no tiene coordenadas y aún no hay
	 *                        plantilla alternativa sin mapa configurada
	 */
	public MensajeWhatsapp armar(WhatsappPendiente aviso) {
		PlantillaWhatsapp plantilla = elegir(aviso);
		UbicacionWhatsapp ubicacion = plantilla.conUbicacion() ? aviso.ubicacion() : null;
		if (plantilla.conUbicacion() && ubicacion == null) {
			throw new EnvioRechazado("La alerta no tiene ubicación GPS y no hay plantilla sin mapa configurada para "
					+ plantilla.nombre());
		}
		List<ParametroPlantilla> parametros = new ArrayList<>();
		for (String variable : plantilla.variables()) {
			parametros.add(new ParametroPlantilla(plantilla.nombrada() ? variable : null, valor(variable, aviso)));
		}
		return new MensajeWhatsapp(aviso.telefono(), plantilla.nombre(), plantilla.idioma(), ubicacion, parametros,
				plantilla.botones());
	}

	/** Elige la plantilla según el nivel del contacto y si hay coordenadas disponibles. */
	private PlantillaWhatsapp elegir(WhatsappPendiente aviso) {
		boolean tieneMapa = aviso.ubicacion() != null;
		NivelConversacion nivel = aviso.nivel();
		return switch (nivel) {
			case SIMULACRO -> elegirConRespaldo(simulacro, simulacroSinMapa, tieneMapa);
			case NIVEL_1_PRLAC, NIVEL_2_TERCERO -> elegirConRespaldo(nivel1Y2, nivel1Y2SinMapa, tieneMapa);
			case NIVEL_3_SERVICIO -> {
				boolean usarRut = usarPlantillaRutCarabineros && "CARABINEROS".equals(aviso.tipoContacto())
						&& nivel3Rut != null;
				yield usarRut ? elegirConRespaldo(nivel3Rut, nivel3RutSinMapa, tieneMapa)
						: elegirConRespaldo(nivel3Edad, nivel3EdadSinMapa, tieneMapa);
			}
		};
	}

	/** Si falta ubicación y existe plantilla sin mapa configurada, la usa automáticamente. */
	private static PlantillaWhatsapp elegirConRespaldo(PlantillaWhatsapp principal, PlantillaWhatsapp sinMapa,
			boolean tieneMapa) {
		if (!tieneMapa && principal.conUbicacion() && sinMapa != null) {
			return sinMapa;
		}
		return principal;
	}

	/** @return el texto no vacío de esa variable para Meta */
	private String valor(String variable, WhatsappPendiente aviso) {
		return switch (variable) {
			case "id_alerta" -> aviso.esSimulacro() ? "SIM-" + aviso.numero() : "#" + aviso.numero();
			case "folio" -> "#" + aviso.numero();
			case "alias_paciente", "quien" -> textoONoVacio(aviso.alias(), quienSinAlias);
			case "nombre_paciente" -> textoONoVacio(aviso.nombreCompleto(),
					textoONoVacio(aviso.alias(), quienSinAlias));
			case "edad" -> aviso.edad() == null ? "Sin dato" : String.valueOf(aviso.edad());
			case "rut_paciente" -> textoONoVacio(aviso.rut(), "Sin dato");
			case "tipo_evento" -> descripcionEvento(aviso.tipo(), aviso.severidad());
			case "gravedad" -> descripcionGravedad(aviso.severidad());
			case "hora_evento" -> formatearHora(aviso.fechaHoraEvento());
			case "nota_adicional" -> notaAdicional(aviso);
			case "numero_reloj" -> textoONoVacio(aviso.telefonoReloj(), "No registrado");
			default -> throw new IllegalStateException("Variable no revisada: " + variable);
		};
	}

	/** Concatena la dirección del paciente mientras la nota adicional dedicada sigue pendiente. */
	private static String notaAdicional(WhatsappPendiente aviso) {
		String direccion = aviso.direccion() == null || aviso.direccion().isBlank() ? null : aviso.direccion().strip();
		if (aviso.esSimulacro()) {
			return direccion == null ? "Esto es un simulacro" : "Esto es un simulacro · Dirección: " + direccion;
		}
		return direccion == null ? "Sin dirección registrada" : "Dirección: " + direccion;
	}

	/** @return nombre legible del tipo de evento para los mensajes de WhatsApp */
	public static String descripcionEvento(TipoEvento tipo, Severidad severidad) {
		return switch (tipo) {
			case CAIDA -> severidad == Severidad.GRAVE ? "Caída con inmovilidad posterior" : "Posible caída detectada";
			case FC -> severidad == Severidad.GRAVE ? "Frecuencia cardíaca grave" : "Frecuencia cardíaca anormal";
			case SPO2 -> severidad == Severidad.GRAVE ? "Saturación de oxígeno grave" : "Saturación de oxígeno baja";
			case FC_SPO2 -> "Descompensación vital multiparámetro";
			case TENDENCIA -> "Tendencia vital fuera de rango";
			case INACTIVIDAD -> "Inactividad prolongada";
			case ZONA_SEGURA -> "Salida de zona segura";
			case BOTON_AYUDA -> "Solicitud de ayuda desde el reloj";
			case SILENCIO_DATOS -> "Silencio de datos del reloj";
			case BATERIA_BAJA -> "Batería baja del reloj";
			case RELOJ_NO_PUESTO -> "Reloj no puesto";
			case EXCESO_ALERTAS -> "Exceso de alertas del reloj";
		};
	}

	/** @return gravedad en formato legible */
	public static String descripcionGravedad(Severidad severidad) {
		return switch (severidad) {
			case LEVE -> "Leve";
			case MEDIA -> "Media";
			case GRAVE -> "Grave";
		};
	}

	/** @return hora HH:mm en zona horaria de Chile */
	private static String formatearHora(Instant instante) {
		Instant hora = instante == null ? Instant.now() : instante;
		return hora.atZone(ZonaHoraria.CHILE).format(HORA);
	}

	/** @return el texto limpio o el respaldo si viene nulo o vacío */
	private static String textoONoVacio(String valor, String respaldo) {
		return valor == null || valor.isBlank() ? respaldo : valor.strip();
	}

	/** @return la plantilla obligatoria validada */
	private static PlantillaWhatsapp revisarObligatoria(String cual, PlantillaWhatsapp plantilla) {
		Objects.requireNonNull(plantilla, "Falta la plantilla " + cual);
		if (!plantilla.configurada()) {
			throw new IllegalArgumentException("Falta el nombre de la plantilla " + cual
					+ " (alertacuida.whatsapp.plantillas." + cual + ".nombre)");
		}
		return revisarCampos(cual, plantilla);
	}

	/** @return la plantilla opcional validada, o null si no está configurada */
	private static PlantillaWhatsapp revisarOpcional(String cual, PlantillaWhatsapp plantilla) {
		if (plantilla == null || !plantilla.configurada()) {
			return null;
		}
		return revisarCampos(cual, plantilla);
	}

	/** Valida idioma, variables y botones de una plantilla configurada. */
	private static PlantillaWhatsapp revisarCampos(String cual, PlantillaWhatsapp plantilla) {
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
			if (AccionWhatsapp.desde(boton).isEmpty()) {
				throw new IllegalArgumentException("La plantilla " + cual + " tiene el botón " + boton
						+ ", que no existe.");
			}
		}
		return plantilla;
	}

}
