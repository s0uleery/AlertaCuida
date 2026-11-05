package cl.ucn.alertacuida.adapters.web.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.application.auditoria.RegistrarLectura;
import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Registro automático de lecturas, sin base de datos: qué se guarda y qué pasa si no se puede guardar. */
class RegistroDeLecturasTest {

	private static final UUID ANA = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final String DATO = "dato-del-paciente";

	/** Inicia la "sesión" de Ana para la petición. */
	@BeforeEach
	void iniciarSesion() {
		Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(ANA.toString()).build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
	}

	/** Deja el contexto limpio para las demás pruebas. */
	@AfterEach
	void cerrarSesion() {
		SecurityContextHolder.clearContext();
	}

	/** Arma el controlador de prueba con el registro indicado. */
	private static MockMvc mvcCon(RegistroAuditoria auditoria) {
		return MockMvcBuilders.standaloneSetup(new ControladorDePrueba())
				.setControllerAdvice(new RegistroDeLecturas(new RegistrarLectura(auditoria, Clock.systemUTC())))
				.build();
	}

	/** Lectura de un objeto: guarda quién, la entidad, la variable de la ruta y la IP. */
	@Test
	void registraElIdentificadorDeLaRuta() throws Exception {
		List<String> filas = new ArrayList<>();

		mvcCon((id, accion, entidad, idEntidad, ip, fecha) -> filas.add(
				String.join(" ", id.toString(), accion, entidad, idEntidad, ip)))
				.perform(get("/objetos/abc")).andExpect(status().isOk());

		assertThat(filas).containsExactly(ANA + " LEER PACIENTE abc 127.0.0.1");
	}

	/** Lectura de una lista: el identificador es LISTA. */
	@Test
	void unaListaSeRegistraComoLista() throws Exception {
		List<String> filas = new ArrayList<>();

		mvcCon((id, accion, entidad, idEntidad, ip, fecha) -> filas.add(idEntidad))
				.perform(get("/objetos")).andExpect(status().isOk());

		assertThat(filas).containsExactly(RegistrarLectura.LISTA);
	}

	/** Un endpoint sin la anotación no se registra. */
	@Test
	void sinLaAnotacionNoSeRegistra() throws Exception {
		List<String> filas = new ArrayList<>();

		mvcCon((id, accion, entidad, idEntidad, ip, fecha) -> filas.add(entidad))
				.perform(get("/sin-auditar")).andExpect(status().isOk());

		assertThat(filas).isEmpty();
	}

	/** Si el registro falla, la petición falla y los datos no salen. */
	@Test
	void siElRegistroFallaLosDatosNoSalen() throws Exception {
		String cuerpo = mvcCon((id, accion, entidad, idEntidad, ip, fecha) -> {
			throw new IllegalStateException("sin base de datos");
		}).perform(get("/objetos/abc")).andExpect(status().isInternalServerError())
				.andReturn().getResponse().getContentAsString();

		assertThat(cuerpo).doesNotContain(DATO);
	}

	/** Endpoints mínimos para ejercitar el registro. */
	@RestController
	static class ControladorDePrueba {

		/** @return un objeto con datos */
		@GetMapping("/objetos/{id}")
		@LecturaAuditada(entidad = "PACIENTE", idVariable = "id")
		Map<String, String> objeto() {
			return Map.of("dato", DATO);
		}

		/** @return una lista con datos */
		@GetMapping("/objetos")
		@LecturaAuditada(entidad = "PACIENTE")
		List<String> lista() {
			return List.of(DATO);
		}

		/** @return datos que no se auditan */
		@GetMapping("/sin-auditar")
		List<String> sinAuditar() {
			return List.of(DATO);
		}

		/** @return el error que vería el navegador, sin datos */
		@ExceptionHandler(IllegalStateException.class)
		@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
		String error() {
			return "error";
		}

	}

}
