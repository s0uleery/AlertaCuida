package cl.ucn.alertacuida.personal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.domain.comun.Run;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Lista del equipo, cambio de rol, aviso técnico por correo y permisos de los roles de punta a
 * punta: HTTP, Spring Security, base de datos y auditoría. Sin Docker se omite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class EquipoYPermisosApiTest {

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private HashRun hashRun;
	@Autowired
	private PasswordEncoder encoder;

	private UUID admin;
	private UUID coordinador;

	/** Un administrador y un coordinador activos, con contraseña "Clave-de-" + nombre + "1". */
	@BeforeEach
	void crearUsuarios() {
		jdbc.sql("DELETE FROM registro_auditoria").update();
		jdbc.sql("DELETE FROM usuario").update();
		admin = crear("11.111.111-1", "ADMINISTRADOR", "Admin", "admin@muni.cl");
		coordinador = crear("22.222.222-2", "COORDINADOR", "Coord", "coord@muni.cl");
	}

	/** Deja los permisos del rol PERSONAL como los pone V6, por si una prueba los cambió. */
	@AfterEach
	void restaurarPermisos() {
		jdbc.sql("DELETE FROM permiso_rol WHERE codigo_rol = 'PERSONAL'").update();
		jdbc.sql("INSERT INTO permiso_rol VALUES ('PERSONAL', 'ALERTAS', 'VER'), ('PERSONAL', 'ALERTAS', 'EDITAR')")
				.update();
	}

	/** Sin token responde 401; el coordinador no tiene permisos de ADMINISTRACION y recibe 403 en todo. */
	@Test
	void sinTokenResponde401YSinLosPermisosResponde403() throws Exception {
		mvc.perform(get("/api/personal")).andExpect(status().isUnauthorized());
		String coord = token("22.222.222-2", "Clave-de-Coord1");

		mvc.perform(get("/api/personal").header("Authorization", "Bearer " + coord)).andExpect(status().isForbidden());
		mvc.perform(get("/api/roles").header("Authorization", "Bearer " + coord)).andExpect(status().isForbidden());
		poner(coord, "/api/personal/" + admin + "/rol", "{\"rol\": \"PERSONAL\"}").andExpect(status().isForbidden());
		poner(coord, "/api/personal/" + coordinador + "/recibe-avisos", "{\"recibeAvisos\": true}")
				.andExpect(status().isForbidden());
		poner(coord, "/api/roles/PERSONAL/permisos", "{\"permisos\": []}").andExpect(status().isForbidden());
	}

	/** La lista trae estado, último acceso y avisos de cada cuenta, por nombre y sin el RUN. */
	@Test
	void laListaTraeEstadoUltimoAccesoYAvisosSinElRun() throws Exception {
		String token = tokenAdmin();
		alta(token, """
				{"run": "12.345.678-5", "nombre": "Beatriz", "correo": "bea@muni.cl", "rol": "PERSONAL",
				"recibeAvisos": true}""").andExpect(status().isCreated());

		mvc.perform(get("/api/personal").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].nombre").value("Admin"))
				.andExpect(jsonPath("$[0].estado").value("ACTIVA"))
				.andExpect(jsonPath("$[0].ultimoAcceso").isNotEmpty())
				.andExpect(jsonPath("$[0].recibeAvisos").value(false))
				.andExpect(jsonPath("$[1].nombre").value("Beatriz"))
				.andExpect(jsonPath("$[1].estado").value("PENDIENTE"))
				.andExpect(jsonPath("$[1].ultimoAcceso").isEmpty())
				.andExpect(jsonPath("$[1].recibeAvisos").value(true))
				.andExpect(jsonPath("$[1].tieneTelefono").value(false))
				.andExpect(jsonPath("$[1].run").doesNotExist())
				.andExpect(jsonPath("$[2].rol").value("COORDINADOR"));
	}

	/** En el alta, el aviso técnico por correo queda apagado si no se marca. */
	@Test
	void enElAltaElAvisoTecnicoQuedaApagadoSiNoSeMarca() throws Exception {
		alta(tokenAdmin(), """
				{"run": "12.345.678-5", "nombre": "Beatriz", "correo": "bea@muni.cl", "rol": "PERSONAL"}""")
				.andExpect(status().isCreated());

		assertThat(jdbc.sql("SELECT recibe_avisos FROM usuario WHERE correo = 'bea@muni.cl'").query(Boolean.class)
				.single()).isFalse();
	}

	/** El administrador cambia el rol de otro y queda auditado con el detalle; el propio responde 409. */
	@Test
	void elCambioDeRolSeGuardaYSeAuditaYElPropioResponde409() throws Exception {
		String token = tokenAdmin();

		poner(token, "/api/personal/" + coordinador + "/rol", "{\"rol\": \"PERSONAL\"}")
				.andExpect(status().isNoContent());
		poner(token, "/api/personal/" + admin + "/rol", "{\"rol\": \"PERSONAL\"}").andExpect(status().isConflict());

		assertThat(rolDe(coordinador)).isEqualTo("PERSONAL");
		assertThat(rolDe(admin)).isEqualTo("ADMINISTRADOR");
		String detalle = jdbc.sql("""
				SELECT detalle::text FROM registro_auditoria
				WHERE accion = 'MODIFICAR' AND entidad = 'USUARIO' AND id_entidad = :id""")
				.param("id", coordinador.toString()).query(String.class).single();
		assertThat(detalle).contains("\"rolAnterior\": \"COORDINADOR\"").contains("\"rolNuevo\": \"PERSONAL\"");
	}

	/** Pasar a coordinador sin teléfono responde 400; con teléfono se guarda cifrado con su opt-in. */
	@Test
	void pasarACoordinadorPideTelefonoQueSeGuardaCifrado() throws Exception {
		String token = tokenAdmin();
		UUID ana = crear("12.345.678-5", "PERSONAL", "Ana", "ana@muni.cl");

		poner(token, "/api/personal/" + ana + "/rol", "{\"rol\": \"COORDINADOR\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("WhatsApp")));
		poner(token, "/api/personal/" + ana + "/rol", """
				{"rol": "COORDINADOR", "telefono": "9 1234 5678", "aceptaWhatsapp": true}""")
				.andExpect(status().isNoContent());

		assertThat(rolDe(ana)).isEqualTo("COORDINADOR");
		String telefono = jdbc.sql("SELECT telefono FROM usuario WHERE id_usuario = :id").param("id", ana)
				.query(String.class).single();
		assertThat(telefono).startsWith("v1:").doesNotContain("12345678");
		assertThat(jdbc.sql("SELECT fecha_opt_in IS NOT NULL FROM usuario WHERE id_usuario = :id").param("id", ana)
				.query(Boolean.class).single()).isTrue();
	}

	/** El interruptor del aviso técnico por correo cambia la columna; una cuenta que no existe responde 404. */
	@Test
	void elAvisoTecnicoSeEnciendeYApaga() throws Exception {
		String token = tokenAdmin();

		poner(token, "/api/personal/" + coordinador + "/recibe-avisos", "{\"recibeAvisos\": true}")
				.andExpect(status().isNoContent());
		assertThat(recibeAvisos(coordinador)).isTrue();
		poner(token, "/api/personal/" + coordinador + "/recibe-avisos", "{\"recibeAvisos\": false}")
				.andExpect(status().isNoContent());
		assertThat(recibeAvisos(coordinador)).isFalse();

		poner(token, "/api/personal/" + UUID.randomUUID() + "/recibe-avisos", "{\"recibeAvisos\": true}")
				.andExpect(status().isNotFound());
		poner(token, "/api/personal/" + coordinador + "/recibe-avisos", "{}").andExpect(status().isBadRequest());
	}

	/** Los roles vienen con sus permisos y el administrador primero. */
	@Test
	void losRolesVienenConSusPermisosYElAdministradorPrimero() throws Exception {
		mvc.perform(get("/api/roles").header("Authorization", "Bearer " + tokenAdmin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].codigo").value("ADMINISTRADOR"))
				.andExpect(jsonPath("$[0].esSistema").value(true))
				.andExpect(jsonPath("$[0].permisos.length()").value(20))
				.andExpect(jsonPath("$[?(@.codigo == 'PERSONAL')].permisos[*]").value(
						org.hamcrest.Matchers.containsInAnyOrder("ALERTAS:VER", "ALERTAS:EDITAR")));
	}

	/** Los permisos de un rol se reemplazan y se auditan; los del administrador y uno inválido se rechazan. */
	@Test
	void losPermisosSeReemplazanYLosDelAdministradorNo() throws Exception {
		String token = tokenAdmin();

		poner(token, "/api/roles/PERSONAL/permisos", "{\"permisos\": [\"ALERTAS:VER\", \"REPORTES:VER\"]}")
				.andExpect(status().isNoContent());
		poner(token, "/api/roles/ADMINISTRADOR/permisos", "{\"permisos\": [\"ALERTAS:VER\"]}")
				.andExpect(status().isConflict());
		poner(token, "/api/roles/PERSONAL/permisos", "{\"permisos\": [\"ALERTAS:BORRAR\"]}")
				.andExpect(status().isBadRequest());
		poner(token, "/api/roles/JEFE/permisos", "{\"permisos\": []}").andExpect(status().isNotFound());

		List<String> permisos = jdbc.sql("""
				SELECT modulo || ':' || accion FROM permiso_rol WHERE codigo_rol = 'PERSONAL' ORDER BY 1""")
				.query(String.class).list();
		assertThat(permisos).containsExactly("ALERTAS:VER", "REPORTES:VER");
		String detalle = jdbc.sql("""
				SELECT detalle::text FROM registro_auditoria
				WHERE entidad = 'PERMISO_ROL' AND id_entidad = 'PERSONAL'""")
				.query(String.class).single();
		assertThat(detalle).contains("REPORTES:VER").contains("ALERTAS:EDITAR");
	}

	/** Crea una cuenta del personal activa con contraseña "Clave-de-" + nombre + "1". */
	private UUID crear(String run, String rol, String nombre, String correo) {
		return jdbc.sql("""
				INSERT INTO usuario (codigo_rol, rut, rut_hash, nombre_profesional, correo, hash_contrasena, activo)
				VALUES (:rol, 'cifrado', :hash, :nombre, :correo, :clave, true)
				RETURNING id_usuario""")
				.param("rol", rol)
				.param("hash", hashRun.de(Run.de(run)))
				.param("nombre", nombre)
				.param("correo", correo)
				.param("clave", encoder.encode("Clave-de-" + nombre + "1"))
				.query(UUID.class)
				.single();
	}

	/** @return el rol guardado de la cuenta */
	private String rolDe(UUID id) {
		return jdbc.sql("SELECT codigo_rol FROM usuario WHERE id_usuario = :id").param("id", id).query(String.class)
				.single();
	}

	/** @return si la cuenta recibe los avisos técnicos por correo */
	private boolean recibeAvisos(UUID id) {
		return jdbc.sql("SELECT recibe_avisos FROM usuario WHERE id_usuario = :id").param("id", id)
				.query(Boolean.class).single();
	}

	/** Pide el alta de una cuenta con ese token. */
	private ResultActions alta(String token, String cuerpo) throws Exception {
		return mvc.perform(post("/api/personal").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	/** Hace un PUT con ese token y cuerpo JSON. */
	private ResultActions poner(String token, String ruta, String cuerpo) throws Exception {
		return mvc.perform(put(ruta).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	/** @return el token de acceso del administrador de prueba */
	private String tokenAdmin() throws Exception {
		return token("11.111.111-1", "Clave-de-Admin1");
	}

	/** Inicia sesión y devuelve el token de acceso. */
	private String token(String run, String contrasena) throws Exception {
		return JsonPath.read(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"run\": \"" + run + "\", \"contrasena\": \"" + contrasena + "\"}"))
				.andReturn().getResponse().getContentAsString(), "$.token");
	}

}
