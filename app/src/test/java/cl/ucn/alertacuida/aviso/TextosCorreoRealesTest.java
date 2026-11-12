package cl.ucn.alertacuida.aviso;

import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.application.aviso.PlantillasCorreo;
import cl.ucn.alertacuida.application.aviso.modelo.CorreoPendiente;
import cl.ucn.alertacuida.application.aviso.modelo.Mensaje;
import cl.ucn.alertacuida.application.aviso.modelo.TextosCorreo;
import cl.ucn.alertacuida.application.respuesta.puerto.FirmaRespuesta;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * Los textos reales de correos.yml cumplen los requisitos del contenido, así que esta prueba falla si una
 * edición del archivo los rompe. Requisitos del cronograma y de las decisiones del 08-11 y el 10-11-2026:
 * <ul>
 * <li>R1 Se cargan sin errores: ningún marcador mal escrito ni texto que falte.</li>
 * <li>R2 Sin datos clínicos: solo el alias del paciente, el folio de la alerta y su gravedad. El tipo de una
 * alerta de salud no se nombra; el de una técnica sí, porque habla del reloj.</li>
 * <li>R3 Si el paciente no tiene alias, se usa el folio, sin dejar huecos.</li>
 * <li>R4 En las graves se le indica al contacto que llame al 131; en las demás, no.</li>
 * <li>R5 El enlace al panel del dashboard va solo al equipo; el contacto solo recibe el de su respuesta.</li>
 * <li>R6 El correo al contacto lleva un solo enlace para responder si va o no (decisión del 12-11-2026).</li>
 * </ul>
 */
class TextosCorreoRealesTest {

	private static final String DASHBOARD = "https://alertacuida.muni.cl";

	/** El token que pone la firma de prueba en los enlaces del contacto. */
	private static final String TOKEN = "token-de-prueba";

	private static PlantillasCorreo plantillas;

	/** R1: lee correos.yml como lo hace el backend y arma las plantillas. */
	@BeforeAll
	static void cargar() throws IOException {
		StandardEnvironment entorno = new StandardEnvironment();
		new YamlPropertySourceLoader().load("correos", new ClassPathResource("correos.yml"))
				.forEach(entorno.getPropertySources()::addLast);
		TextosCorreo textos = new Binder(ConfigurationPropertySources.get(entorno))
				.bind("alertacuida.correo.textos", TextosCorreo.class).get();
		FirmaRespuesta firma = new FirmaRespuesta() {
			@Override
			public String firmar(UUID idIntento) {
				return TOKEN;
			}

			@Override
			public Optional<UUID> verificar(String token) {
				return Optional.empty();
			}
		};
		plantillas = new PlantillasCorreo(DASHBOARD, textos, firma);
	}

	/** R2: al equipo, una grave lleva el folio, el alias, la gravedad y el enlace al dashboard. */
	@Test
	void alertaGraveAlEquipo() {
		Mensaje mensaje = plantillas.redactar(alEquipo(TipoEvento.CAIDA, Severidad.GRAVE, "Rosa"));

		assertThat(mensaje.asunto()).contains("grave", "#142", "Rosa");
		assertThat(mensaje.texto()).contains("#142", "Rosa", DASHBOARD);
	}

	/** R2: el tipo de una alerta de salud no aparece, en ninguna plantilla. */
	@Test
	void alertaDeSaludNoNombraLoDetectado() {
		String todo = (texto(alEquipo(TipoEvento.FC_SPO2, Severidad.GRAVE, "Rosa"))
				+ texto(aContacto(TipoEvento.CAIDA, Severidad.GRAVE, "Rosa"))).toLowerCase();

		assertThat(todo).doesNotContain("caída", "fc", "spo2", "frecuencia", "oxígeno", "saturación");
	}

	/** R2: una técnica dice qué le pasa al reloj. */
	@Test
	void alertaTecnicaDiceQuePasaConElReloj() {
		assertThat(plantillas.redactar(alEquipo(TipoEvento.BATERIA_BAJA, Severidad.LEVE, "Rosa")).asunto())
				.contains("técnica", "batería baja", "#142");
	}

	/** R2: el silencio de datos tiene su propia plantilla, con el enlace al dashboard. */
	@Test
	void silencioDeDatosAlEquipo() {
		Mensaje mensaje = plantillas.redactar(alEquipo(TipoEvento.SILENCIO_DATOS, Severidad.MEDIA, "Rosa"));

		assertThat(mensaje.asunto()).contains("dejó de sincronizar", "Rosa");
		assertThat(mensaje.texto()).contains("#142", DASHBOARD);
	}

	/** R3: sin alias, cada plantilla identifica la alerta por su folio y no deja huecos. */
	@Test
	void sinAliasBastaElFolio() {
		for (CorreoPendiente correo : new CorreoPendiente[] {
				alEquipo(TipoEvento.CAIDA, Severidad.GRAVE, null),
				alEquipo(TipoEvento.BATERIA_BAJA, Severidad.LEVE, null),
				alEquipo(TipoEvento.SILENCIO_DATOS, Severidad.MEDIA, null),
				aContacto(TipoEvento.CAIDA, Severidad.GRAVE, "  ")}) {
			Mensaje mensaje = plantillas.redactar(correo);
			assertThat(mensaje.asunto()).contains("#142").doesNotContain("null", "  ", " de :", " )");
			assertThat(mensaje.texto()).doesNotContain("null", "{", "}");
		}
	}

	/** R4: al contacto de una grave se le pide llamar al 131. */
	@Test
	void contactoDeUnaGraveDebeLlamarAl131() {
		Mensaje mensaje = plantillas.redactar(aContacto(TipoEvento.CAIDA, Severidad.GRAVE, "Rosa"));

		assertThat(mensaje.asunto()).contains("Rosa");
		assertThat(mensaje.texto()).contains("Rosa", "#142", "131");
	}

	/** R4: en una media no se pide llamar al SAMU. */
	@Test
	void contactoDeUnaMediaSinSamu() {
		assertThat(texto(aContacto(TipoEvento.CAIDA, Severidad.MEDIA, "Rosa"))).doesNotContain("131", "SAMU");
	}

	/** R5: fuera del enlace de su respuesta, el contacto no recibe ninguna dirección del dashboard. */
	@Test
	void contactoNoRecibeElDashboard() {
		String sinEnlace = texto(aContacto(TipoEvento.CAIDA, Severidad.GRAVE, "Rosa"))
				.replace(DASHBOARD + "/responder#" + TOKEN, "");

		assertThat(sinEnlace).doesNotContain(DASHBOARD);
	}

	/** R6: el contacto recibe un solo enlace de respuesta. */
	@Test
	void contactoRecibeUnSoloEnlace() {
		String texto = texto(aContacto(TipoEvento.CAIDA, Severidad.MEDIA, "Rosa"));

		assertThat(texto).contains(DASHBOARD + "/responder#" + TOKEN);
		assertThat(texto.split("/responder", -1)).hasSize(2);
	}

	/** @return asunto y cuerpo del correo, juntos */
	private static String texto(CorreoPendiente correo) {
		Mensaje mensaje = plantillas.redactar(correo);
		return mensaje.asunto() + "\n" + mensaje.texto();
	}

	/** @return un correo pendiente a una cuenta del equipo por la alerta #142 */
	private static CorreoPendiente alEquipo(TipoEvento tipo, Severidad severidad, String alias) {
		return new CorreoPendiente(UUID.randomUUID(), "ana@muni.cl", true, 142, alias, tipo, severidad, 0);
	}

	/** @return un correo pendiente a un contacto del paciente por la alerta #142 */
	private static CorreoPendiente aContacto(TipoEvento tipo, Severidad severidad, String alias) {
		return new CorreoPendiente(UUID.randomUUID(), "hija@correo.cl", false, 142, alias, tipo, severidad, 0);
	}

}
