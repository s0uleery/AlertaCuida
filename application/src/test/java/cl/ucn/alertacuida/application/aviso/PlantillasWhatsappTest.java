package cl.ucn.alertacuida.application.aviso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.aviso.error.EnvioRechazado;
import cl.ucn.alertacuida.application.aviso.modelo.MensajeWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.NivelConversacion;
import cl.ucn.alertacuida.application.aviso.modelo.ParametroPlantilla;
import cl.ucn.alertacuida.application.aviso.modelo.PlantillaWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.Proposito;
import cl.ucn.alertacuida.application.aviso.modelo.UbicacionWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.WhatsappPendiente;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Elección y relleno de plantillas aprobadas de Meta (26-11-2026): Nivel 1 y 2, Nivel 3 (edad por defecto y
 * RUT opcional para Carabineros), Simulacro, parámetros NAMED, encabezado LOCATION y respaldo sin mapa.
 */
class PlantillasWhatsappTest {

	private static final Instant HORA = Instant.parse("2026-11-26T18:12:00Z"); // 15:12 hora de Chile
	private static final UbicacionWhatsapp GPS = new UbicacionWhatsapp(-29.9533, -71.3436, "Ubicación del paciente",
			"Av. Costanera 123");

	private static final PlantillaWhatsapp NIVEL_1_Y_2 = new PlantillaWhatsapp("aviso_inicial_nivel_1_y_2", "es_CL",
			true, true, List.of("id_alerta", "alias_paciente", "tipo_evento", "gravedad", "hora_evento",
					"nota_adicional", "numero_reloj"),
			List.of("TOMAR_LLAMADA", "NO_DISPONIBLE"));
	private static final PlantillaWhatsapp NIVEL_1_Y_2_SIN_MAPA = new PlantillaWhatsapp(
			"aviso_inicial_nivel_1_y_2_sin_mapa", "es_CL", true, false,
			List.of("id_alerta", "alias_paciente", "nota_adicional"), List.of("TOMAR_LLAMADA", "NO_DISPONIBLE"));
	private static final PlantillaWhatsapp NIVEL_3_EDAD = new PlantillaWhatsapp("aviso_inicial_nivel_3_edad", "es_CL",
			true, true, List.of("id_alerta", "alias_paciente", "nombre_paciente", "edad", "tipo_evento",
					"hora_evento", "nota_adicional"),
			List.of("ATENDEREMOS", "NO_DISPONIBLE"));
	private static final PlantillaWhatsapp NIVEL_3_RUT = new PlantillaWhatsapp("aviso_inicial_nivel_3_rut", "es_CL",
			true, true, List.of("id_alerta", "alias_paciente", "nombre_paciente", "edad", "rut_paciente",
					"tipo_evento", "hora_evento", "nota_adicional"),
			List.of("ATENDEREMOS", "NO_DISPONIBLE"));
	private static final PlantillaWhatsapp SIMULACRO = new PlantillaWhatsapp("simulacro_aviso_inicial", "es_CL",
			true, true, List.of("id_alerta", "alias_paciente", "nombre_paciente", "edad", "rut_paciente",
					"tipo_evento", "hora_evento", "nota_adicional"),
			List.of("ATENDER_SIMULACRO", "NO_DISPONIBLE"));

	private final PlantillasWhatsapp plantillas = new PlantillasWhatsapp(NIVEL_1_Y_2, NIVEL_1_Y_2_SIN_MAPA,
			NIVEL_3_EDAD, null, NIVEL_3_RUT, null, SIMULACRO, null, false, "la persona que usted apoya");

	/** Nivel 1 y 2 usan aviso_inicial_nivel_1_y_2 con parámetros nombrados, GPS y dirección en nota adicional. */
	@Test
	void nivel1Y2ArmanLaPlantillaConParametrosNombradosYGps() {
		MensajeWhatsapp mensaje = plantillas.armar(pendiente(NivelConversacion.NIVEL_2_TERCERO,
				"TERCERO_SIGNIFICATIVO", Severidad.GRAVE, false, GPS));

		assertThat(mensaje.plantilla()).isEqualTo("aviso_inicial_nivel_1_y_2");
		assertThat(mensaje.idioma()).isEqualTo("es_CL");
		assertThat(mensaje.ubicacion()).isEqualTo(GPS);
		assertThat(mensaje.parametros()).containsExactly(
				new ParametroPlantilla("id_alerta", "#142"),
				new ParametroPlantilla("alias_paciente", "Rosa"),
				new ParametroPlantilla("tipo_evento", "Caída con inmovilidad posterior"),
				new ParametroPlantilla("gravedad", "Grave"),
				new ParametroPlantilla("hora_evento", "15:12"),
				new ParametroPlantilla("nota_adicional", "Dirección: Av. Costanera 123"),
				new ParametroPlantilla("numero_reloj", "+56987654321"));
		assertThat(mensaje.botones()).containsExactly("TOMAR_LLAMADA", "NO_DISPONIBLE");
	}

	/** Nivel 3 usa por defecto la plantilla de edad (incluso para Carabineros mientras el flag esté apagado). */
	@Test
	void nivel3UsaEdadPorDefectoYRutCuandoSeHabilitaParaCarabineros() {
		WhatsappPendiente carabineros = pendiente(NivelConversacion.NIVEL_3_SERVICIO, "CARABINEROS",
				Severidad.GRAVE, false, GPS);

		MensajeWhatsapp conEdad = plantillas.armar(carabineros);
		assertThat(conEdad.plantilla()).isEqualTo("aviso_inicial_nivel_3_edad");
		assertThat(conEdad.parametros()).extracting(ParametroPlantilla::nombre)
				.doesNotContain("rut_paciente");

		PlantillasWhatsapp conFlagRut = new PlantillasWhatsapp(NIVEL_1_Y_2, null, NIVEL_3_EDAD, null,
				NIVEL_3_RUT, null, SIMULACRO, null, true, "alguien");
		MensajeWhatsapp conRut = conFlagRut.armar(carabineros);
		assertThat(conRut.plantilla()).isEqualTo("aviso_inicial_nivel_3_rut");
		assertThat(conRut.parametros()).contains(new ParametroPlantilla("rut_paciente", "12.345.678-5"));
	}

	/** Simulacro usa simulacro_aviso_inicial con prefijo SIM- y botones de simulacro. */
	@Test
	void unSimulacroUsaLaPlantillaDeSimulacro() {
		MensajeWhatsapp simulacro = plantillas.armar(pendiente(NivelConversacion.SIMULACRO, "TERCERO_SIGNIFICATIVO",
				Severidad.GRAVE, true, GPS));

		assertThat(simulacro.plantilla()).isEqualTo("simulacro_aviso_inicial");
		assertThat(simulacro.parametros()).contains(new ParametroPlantilla("id_alerta", "SIM-142"));
		assertThat(simulacro.botones()).containsExactly("ATENDER_SIMULACRO", "NO_DISPONIBLE");
	}

	/** Sin GPS usa la plantilla sin mapa si está configurada; si no existe, rechaza para caer al correo. */
	@Test
	void sinGpsUsaPlantillaSinMapaORechazaSiNoEstaConfigurada() {
		MensajeWhatsapp sinMapa = plantillas.armar(pendiente(NivelConversacion.NIVEL_1_PRLAC, "PRLAC",
				Severidad.MEDIA, false, null));
		assertThat(sinMapa.plantilla()).isEqualTo("aviso_inicial_nivel_1_y_2_sin_mapa");
		assertThat(sinMapa.ubicacion()).isNull();

		assertThatThrownBy(() -> plantillas.armar(pendiente(NivelConversacion.NIVEL_3_SERVICIO, "CESFAM",
				Severidad.GRAVE, false, null)))
				.isInstanceOf(EnvioRechazado.class)
				.hasMessageContaining("ubicación GPS");
	}

	/** Una variable o un botón que no existe se rechaza al configurar, no al enviar. */
	@Test
	void unaVariableOBotonDesconocidoSeRechazaAlConfigurar() {
		assertThatThrownBy(() -> new PlantillasWhatsapp(
				new PlantillaWhatsapp("x", "es_CL", true, false, List.of("diagnostico"), List.of()), null,
				NIVEL_3_EDAD, null, null, null, SIMULACRO, null, false, "alguien"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("diagnostico");
		assertThatThrownBy(() -> new PlantillasWhatsapp(
				new PlantillaWhatsapp("x", "es_CL", true, false, List.of(), List.of("TAL_VEZ")), null,
				NIVEL_3_EDAD, null, null, null, SIMULACRO, null, false, "alguien"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("TAL_VEZ");
		assertThatThrownBy(() -> new PlantillasWhatsapp(
				new PlantillaWhatsapp(" ", "es_CL", true, false, List.of(), List.of()), null,
				NIVEL_3_EDAD, null, null, null, SIMULACRO, null, false, "alguien"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	/** @return un aviso pendiente completo de Rosa, alerta #142 */
	private static WhatsappPendiente pendiente(NivelConversacion nivel, String tipoContacto, Severidad severidad,
			boolean simulacro, UbicacionWhatsapp gps) {
		return new WhatsappPendiente(UUID.randomUUID(), UUID.randomUUID(), "+56911111111", 142, "Rosa",
				"Rosa Morales Díaz", 84, "12.345.678-5", "Av. Costanera 123", "+56987654321", TipoEvento.CAIDA,
				severidad, HORA, gps, simulacro, 0, nivel == NivelConversacion.NIVEL_1_PRLAC, tipoContacto,
				Proposito.AVISO);
	}

}
