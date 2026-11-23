package cl.ucn.alertacuida.reloj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.domain.comun.Run;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Emparejamiento del reloj con código, de punta a punta: el reloj pide un código sin token, el
 * coordinador lo escribe en el dashboard y el token del reloj empieza a servir. Sin Docker se omite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class RequisitosEmparejamientoTest {

	private static final String EMPAREJAMIENTOS = "/api/reloj/v1/emparejamientos";

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private HashRun hashRun;
	@Autowired
	private PasswordEncoder encoder;
	@Autowired
	private SecretosUnSoloUso secretos;

	private String coordinador;
	private String paciente;
	private String reloj;

	/** Base vacía con un coordinador, un paciente con consentimiento y un reloj operativo libre. */
	@BeforeEach
	void preparar() throws Exception {
		for (String tabla : new String[] {"emparejamiento_reloj", "asignacion_reloj", "smartwatch", "paciente",
				"registro_auditoria", "usuario"}) {
			jdbc.sql("DELETE FROM " + tabla).update();
		}
		jdbc.sql("""
				INSERT INTO usuario (codigo_rol, rut, rut_hash, nombre_profesional, correo, hash_contrasena, activo)
				VALUES ('COORDINADOR', 'cifrado', :hash, 'Coord', 'coord@muni.cl', :clave, true)""")
				.param("hash", hashRun.de(Run.de("11.111.111-1")))
				.param("clave", encoder.encode("Clave-de-Coord1"))
				.update();
		String sesion = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"run\": \"11.111.111-1\", \"contrasena\": \"Clave-de-Coord1\"}"))
				.andReturn().getResponse().getContentAsString();
		coordinador = JsonPath.read(sesion, "$.token");
		paciente = id(delCoordinador(post("/api/pacientes"), """
				{"run": "12345678-5", "nombres": "María", "apellidos": "Rojas", "fechaNacimiento": "1940-03-15",
				"nivelDependencia": "SEVERA", "fechaConsentimiento": "2026-01-10", "fechaIngreso": "2026-01-10"}""")
				.andExpect(status().isCreated()));
		reloj = id(delCoordinador(post("/api/relojes"), "{\"identificadorReloj\": \"R-001\"}")
				.andExpect(status().isCreated()));
	}

	/**
	 * El reloj pide un código sin token; mientras nadie lo escribe su token no sirve. El coordinador lo
	 * escribe (como sea: minúsculas, espacios) y desde ahí el token sirve. La respuesta no trae token y la
	 * base solo guarda su hash.
	 */
	@Test
	void elRelojPideUnCodigoYSuTokenSirveCuandoElCoordinadorLoEscribe() throws Exception {
		String respuesta = mvc.perform(post(EMPAREJAMIENTOS))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.codigo").value(matchesPattern("[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}")))
				.andExpect(jsonPath("$.segundosConsulta").value(5))
				.andReturn().getResponse().getContentAsString();
		String codigo = JsonPath.read(respuesta, "$.codigo");
		String token = JsonPath.read(respuesta, "$.token");

		consultar(token).andExpect(jsonPath("$.estado").value("PENDIENTE"));
		registrarFcm(token).andExpect(status().isUnauthorized());

		vincular(codigo.toLowerCase().replace("-", " "))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.token").doesNotExist());

		consultar(token).andExpect(jsonPath("$.estado").value("VINCULADO"));
		registrarFcm(token).andExpect(status().isNoContent());
		assertThat(jdbc.sql("SELECT hash_token_reloj FROM asignacion_reloj").query(String.class).single())
				.isEqualTo(secretos.hash(token));
		assertThat(jdbc.sql("SELECT count(*) FROM emparejamiento_reloj").query(Integer.class).single()).isZero();
	}

	/**
	 * Sin código, o con uno inventado, ya usado o vencido, responde 400 y no vincula: no hay forma de vincular
	 * entregando un token a mano. El vencido se informa al reloj.
	 */
	@Test
	void sinCodigoOConUnoInventadoUsadoOVencidoNoVincula() throws Exception {
		delCoordinador(post("/api/pacientes/" + paciente + "/reloj"), "{\"idDispositivo\": \"" + reloj + "\"}")
				.andExpect(status().isBadRequest());
		delCoordinador(post("/api/pacientes/" + paciente + "/reloj/token"), "{}")
				.andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));
		vincular("ZZZZ-ZZZZ").andExpect(status().isBadRequest());

		String respuesta = mvc.perform(post(EMPAREJAMIENTOS)).andReturn().getResponse().getContentAsString();
		jdbc.sql("""
				UPDATE emparejamiento_reloj SET fecha_creacion = now() - interval '11 minutes',
				fecha_expiracion = now() - interval '1 minute'""").update();

		vincular(JsonPath.read(respuesta, "$.codigo")).andExpect(status().isBadRequest());
		consultar(JsonPath.read(respuesta, "$.token")).andExpect(jsonPath("$.estado").value("VENCIDO"));
		assertThat(jdbc.sql("SELECT count(*) FROM asignacion_reloj").query(Integer.class).single()).isZero();
	}

	/** Si el vínculo falla (reloj fuera de servicio), el código no se gasta: se puede usar después. */
	@Test
	void unVinculoFallidoNoGastaElCodigo() throws Exception {
		String codigo = JsonPath.read(mvc.perform(post(EMPAREJAMIENTOS)).andReturn().getResponse()
				.getContentAsString(), "$.codigo");
		jdbc.sql("UPDATE smartwatch SET estado = 'EN_REPARACION'").update();
		vincular(codigo).andExpect(status().isConflict());

		jdbc.sql("UPDATE smartwatch SET estado = 'OPERATIVO'").update();
		vincular(codigo).andExpect(status().isCreated());
	}

	/** Emparejar de nuevo el mismo reloj: el token anterior deja de servir y el nuevo sirve. */
	@Test
	void emparejarDeNuevoCambiaElTokenDelMismoVinculo() throws Exception {
		String primero = mvc.perform(post(EMPAREJAMIENTOS)).andReturn().getResponse().getContentAsString();
		vincular(JsonPath.read(primero, "$.codigo")).andExpect(status().isCreated());
		String anterior = JsonPath.read(primero, "$.token");
		String respuesta = mvc.perform(post(EMPAREJAMIENTOS)).andReturn().getResponse().getContentAsString();

		delCoordinador(post("/api/pacientes/" + paciente + "/reloj/emparejamiento"),
				"{\"codigoEmparejamiento\": \"" + JsonPath.read(respuesta, "$.codigo") + "\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").doesNotExist());

		registrarFcm(anterior).andExpect(status().isUnauthorized());
		registrarFcm(JsonPath.read(respuesta, "$.token")).andExpect(status().isNoContent());
		assertThat(jdbc.sql("SELECT count(*) FROM asignacion_reloj").query(Integer.class).single()).isEqualTo(1);
	}

	/** Con demasiados códigos pendientes, la ruta pública responde 429 con Retry-After. */
	@Test
	void conDemasiadosCodigosPendientesResponde429() throws Exception {
		jdbc.sql("""
				INSERT INTO emparejamiento_reloj (hash_codigo, hash_token, fecha_creacion, fecha_expiracion)
				SELECT 'c' || n, 't' || n, now(), now() + interval '10 minutes' FROM generate_series(1, 50) n""")
				.update();

		mvc.perform(post(EMPAREJAMIENTOS))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "60"))
				.andExpect(jsonPath("$.codigo").value("DEMASIADAS_PETICIONES"));
	}

	/** El reloj pregunta por su emparejamiento. */
	private ResultActions consultar(String token) throws Exception {
		return mvc.perform(post(EMPAREJAMIENTOS + "/consulta").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\": \"" + token + "\"}"))
				.andExpect(status().isOk());
	}

	/** Una llamada cualquiera de la API del reloj con su token: registrar el token de Firebase. */
	private ResultActions registrarFcm(String token) throws Exception {
		return mvc.perform(put("/api/reloj/v1/token-fcm").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"token\": \"fcm-de-prueba\"}"));
	}

	/** El coordinador vincula el reloj al paciente con ese código. */
	private ResultActions vincular(String codigo) throws Exception {
		return delCoordinador(post("/api/pacientes/" + paciente + "/reloj"),
				"{\"idDispositivo\": \"" + reloj + "\", \"codigoEmparejamiento\": \"" + codigo + "\"}");
	}

	/** Petición del coordinador con cuerpo JSON. */
	private ResultActions delCoordinador(MockHttpServletRequestBuilder peticion, String cuerpo) throws Exception {
		return mvc.perform(peticion.header("Authorization", "Bearer " + coordinador)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	/** @return el "id" de la respuesta de un alta */
	private static String id(ResultActions respuesta) throws Exception {
		return JsonPath.read(respuesta.andReturn().getResponse().getContentAsString(), "$.id");
	}

}
