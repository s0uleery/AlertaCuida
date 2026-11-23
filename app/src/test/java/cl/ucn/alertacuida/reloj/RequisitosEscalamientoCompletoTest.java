package cl.ucn.alertacuida.reloj;

import static cl.ucn.alertacuida.soporte.EscenarioEscalamiento.LUNES_MEDIODIA;
import static cl.ucn.alertacuida.soporte.EscenarioEscalamiento.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.application.aviso.EnviarCorreosPendientes;
import cl.ucn.alertacuida.application.aviso.PlantillasCorreo;
import cl.ucn.alertacuida.application.aviso.puerto.BandejaSalida;
import cl.ucn.alertacuida.application.avisoreloj.modelo.TipoAvisoReloj;
import cl.ucn.alertacuida.application.avisoreloj.puerto.BandejaAvisosReloj;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.application.escalamiento.AvisarOficina;
import cl.ucn.alertacuida.application.escalamiento.VigilarEscalamiento;
import cl.ucn.alertacuida.soporte.EscenarioEscalamiento;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import cl.ucn.alertacuida.soporte.RelojAjustable;
import cl.ucn.alertacuida.soporte.RelojDePrueba;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Escalamiento dirigido por el reloj, de punta a punta por la API, contra la base real y con un reloj de
 * pruebas que avanza minuto a minuto en un lunes hábil. Sale de la tarea del escalamiento del cronograma
 * ("una alerta recorre la lista de contactos, cada aviso pedido sale, el reloj se detiene al confirmarse y una
 * alerta sin noticias del reloj avisa a los profesionales"), de docs/api-reloj.md y de docs/alertas.md. Sin
 * Docker se omite.
 * <ul>
 * <li>E1 Mientras nadie confirma, el estado dice AVISANDO_CONTACTO y el reloj sigue con el siguiente contacto;
 * cada aviso pedido sale.</li>
 * <li>E2 Cuando un contacto responde "Voy en camino", el estado dice CONFIRMADA, quién confirmó y que el reloj
 * se detenga; queda el aviso ALERTA_CONFIRMADA para el reloj y un aviso pedido después se rechaza con
 * ALERTA_TERMINADA. El vigilante no avisa por una alerta confirmada.</li>
 * <li>E3 Reloj sin señal a mitad del recorrido: a los 15 minutos sin noticias el vigilante avisa al coordinador,
 * una sola vez; antes, no.</li>
 * <li>E4 Cuando el reloj recupera la señal y reenvía la alerta y la solicitud que ya había enviado, no se crea
 * nada dos veces y el recorrido sigue desde donde iba.</li>
 * <li>E5 Un coordinador confirma desde el correo del aviso a la oficina: la alerta queda confirmada con él
 * como contacto y el reloj se detiene (decisión del 16-11-2026).</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ PostgresDePrueba.class, RelojDePrueba.class })
@Testcontainers(disabledWithoutDocker = true)
class RequisitosEscalamientoCompletoTest {

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private CifradoCampos cifrado;
	@Autowired
	private SecretosUnSoloUso secretos;
	@Autowired
	private RelojAjustable reloj;
	@Autowired
	private EnviarCorreosPendientes enviarCorreos;
	@Autowired
	private VigilarEscalamiento vigilante;
	@Autowired
	private BandejaSalida bandeja;
	@Autowired
	private PlantillasCorreo plantillas;
	@Autowired
	private AvisarOficina oficina;
	@Autowired
	private BandejaAvisosReloj bandejaReloj;

	private EscenarioEscalamiento escenario;
	private final UUID eventAlerta = UUID.randomUUID();

	/** Rosa, su reloj, la hija, el vecino y un coordinador; el reloj de pruebas en el lunes a mediodía. */
	@BeforeEach
	void preparar() {
		reloj.fijar(LUNES_MEDIODIA);
		escenario = new EscenarioEscalamiento(jdbc, cifrado, secretos);
		escenario.preparar();
	}

	/** E1 y E2: la alerta recorre la lista hasta que el vecino confirma, y entonces el reloj se detiene. */
	@Test
	void recorreLaListaHastaQueAlguienConfirma() throws Exception {
		enviarAlerta().andExpect(status().isCreated());
		estado().andExpect(jsonPath("$.estado").value("RECIBIDA"))
				.andExpect(jsonPath("$.detenerEscalamiento").value(false));

		reloj.adelantar(Duration.ofMinutes(1));
		UUID avisoHija = aceptado(pedirAviso(UUID.randomUUID(), escenario.hija(), 1));
		enviarCorreos.enviar();
		assertThat(escenario.enviado(avisoHija)).isTrue();

		reloj.adelantar(Duration.ofMinutes(5));
		estado().andExpect(jsonPath("$.estado").value("AVISANDO_CONTACTO"))
				.andExpect(jsonPath("$.detenerEscalamiento").value(false))
				.andExpect(jsonPath("$.confirmadaPor").doesNotExist());
		UUID avisoVecino = aceptado(pedirAviso(UUID.randomUUID(), escenario.vecino(), 1));
		String token = EscenarioEscalamiento.tokenDelCorreo(bandeja, plantillas, reloj.instant(), "vecino@correo.cl");
		enviarCorreos.enviar();
		assertThat(escenario.enviado(avisoVecino)).isTrue();

		reloj.adelantar(Duration.ofMinutes(2));
		mvc.perform(post("/api/respuestas-contacto").contentType(MediaType.APPLICATION_JSON)
				.content(EscenarioEscalamiento.respuesta(token, "VOY_EN_CAMINO"))).andExpect(status().isOk());

		estado().andExpect(jsonPath("$.estado").value("CONFIRMADA"))
				.andExpect(jsonPath("$.detenerEscalamiento").value(true))
				.andExpect(jsonPath("$.confirmadaPor.idContacto").value(escenario.vecino().toString()));
		assertThat(escenario.avisosConfirmadaAlReloj(eventAlerta)).isEqualTo(1);
		pedirAviso(UUID.randomUUID(), escenario.hija(), 2).andExpect(status().isOk())
				.andExpect(jsonPath("$.resultado").value("RECHAZADO"))
				.andExpect(jsonPath("$.motivo").value("ALERTA_TERMINADA"))
				.andExpect(jsonPath("$.estadoAlerta.detenerEscalamiento").value(true));
		assertThat(escenario.avisosA(eventAlerta, escenario.hija())).isEqualTo(1);

		reloj.adelantar(Duration.ofMinutes(20));
		vigilante.vigilar();
		assertThat(escenario.correosDelVigilante(eventAlerta)).isZero();
	}

	/** E3 y E4: el reloj se queda sin señal, el vigilante avisa una vez y, al volver, nada se repite. */
	@Test
	void relojSinSenalAvisaAlCoordinadorYAlVolverNoRepite() throws Exception {
		enviarAlerta().andExpect(status().isCreated());
		reloj.adelantar(Duration.ofMinutes(1));
		UUID solicitudHija = UUID.randomUUID();
		UUID avisoHija = aceptado(pedirAviso(solicitudHija, escenario.hija(), 1));
		enviarCorreos.enviar();

		reloj.adelantar(Duration.ofMinutes(10));
		vigilante.vigilar();
		assertThat(escenario.correosDelVigilante(eventAlerta)).as("10 minutos sin noticias").isZero();

		reloj.adelantar(Duration.ofMinutes(6));
		vigilante.vigilar();
		vigilante.vigilar();
		assertThat(escenario.correosDelVigilante(eventAlerta)).as("16 minutos sin noticias").isEqualTo(1);

		reloj.adelantar(Duration.ofMinutes(4));
		enviarAlerta().andExpect(status().isOk());
		assertThat(aceptado(pedirAviso(solicitudHija, escenario.hija(), 1))).isEqualTo(avisoHija);
		assertThat(escenario.alertasCon(eventAlerta)).isEqualTo(1);
		assertThat(escenario.avisosA(eventAlerta, escenario.hija())).isEqualTo(1);
		estado().andExpect(jsonPath("$.estado").value("AVISANDO_CONTACTO"))
				.andExpect(jsonPath("$.detenerEscalamiento").value(false));

		aceptado(pedirAviso(UUID.randomUUID(), escenario.vecino(), 1));
		vigilante.vigilar();
		assertThat(escenario.avisosA(eventAlerta, escenario.vecino())).isEqualTo(1);
		assertThat(escenario.correosDelVigilante(eventAlerta)).isEqualTo(1);
	}

	/**
	 * E5: el coordinador recibe por correo el aviso a la oficina, con su enlace. Si confirma que va, la alerta
	 * queda confirmada con él como contacto, el reloj recibe la orden de detenerse y ya no avisa a nadie más.
	 */
	@Test
	void unCoordinadorConfirmaDesdeSuCorreoYElRelojSeDetiene() throws Exception {
		jdbc.sql("UPDATE smartwatch SET token_fcm = 'token-fcm-escalamiento'").update();
		enviarAlerta().andExpect(status().isCreated());
		reloj.adelantar(Duration.ofMinutes(1));
		aceptado(pedirAviso(UUID.randomUUID(), escenario.hija(), 1));
		oficina.avisar();
		String token = EscenarioEscalamiento.tokenDelCorreo(bandeja, plantillas, reloj.instant(),
				EscenarioEscalamiento.CORREO_COORDINADOR);

		reloj.adelantar(Duration.ofMinutes(2));
		mvc.perform(post("/api/respuestas-contacto").contentType(MediaType.APPLICATION_JSON)
				.content(EscenarioEscalamiento.respuesta(token, "VOY_EN_CAMINO"))).andExpect(status().isOk());

		UUID coordinador = escenario.contactoDelCoordinador();
		estado().andExpect(jsonPath("$.estado").value("CONFIRMADA"))
				.andExpect(jsonPath("$.detenerEscalamiento").value(true))
				.andExpect(jsonPath("$.confirmadaPor.idContacto").value(coordinador.toString()));
		assertThat(bandejaReloj.sinAcusar(3)).filteredOn(a -> a.tipo() == TipoAvisoReloj.ALERTA_CONFIRMADA)
				.singleElement().satisfies(a -> assertThat(a.idContacto()).isEqualTo(coordinador));
		pedirAviso(UUID.randomUUID(), escenario.vecino(), 1).andExpect(jsonPath("$.resultado").value("RECHAZADO"))
				.andExpect(jsonPath("$.motivo").value("ALERTA_TERMINADA"));
	}

	/** El reloj envía (o reenvía) la caída de Rosa, con la hora del reloj de pruebas. */
	private ResultActions enviarAlerta() throws Exception {
		return mvc.perform(post("/api/reloj/v1/alertas").header("Authorization", "Bearer " + TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(EscenarioEscalamiento.alerta(eventAlerta, reloj.instant())));
	}

	/** El reloj pide avisar por correo a un contacto de la alerta. */
	private ResultActions pedirAviso(UUID eventId, UUID contacto, int ronda) throws Exception {
		return mvc.perform(post("/api/reloj/v1/alertas/" + eventAlerta + "/avisos")
				.header("Authorization", "Bearer " + TOKEN).contentType(MediaType.APPLICATION_JSON)
				.content(EscenarioEscalamiento.solicitud(eventId, contacto, ronda)));
	}

	/** El reloj consulta el estado de la alerta. */
	private ResultActions estado() throws Exception {
		return mvc.perform(get("/api/reloj/v1/alertas/" + eventAlerta).header("Authorization", "Bearer " + TOKEN))
				.andExpect(status().isOk());
	}

	/** @return el idIntento de una solicitud aceptada */
	private static UUID aceptado(ResultActions respuesta) throws Exception {
		String cuerpo = respuesta.andExpect(status().isOk()).andExpect(jsonPath("$.resultado").value("ACEPTADO"))
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(cuerpo, "$.idIntento"));
	}

}
