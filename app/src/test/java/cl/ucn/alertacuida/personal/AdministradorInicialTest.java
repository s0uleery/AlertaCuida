package cl.ucn.alertacuida.personal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.personal.CrearAdministradorInicial;
import cl.ucn.alertacuida.configuracion.AdministradorInicialAlArrancar;
import cl.ucn.alertacuida.configuracion.PropiedadesAdministradorInicial;
import cl.ucn.alertacuida.soporte.PostgresDePrueba;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Primer administrador de un servidor sin datos de ejemplo, contra PostgreSQL real: se crea al arrancar
 * solo si no hay ningún administrador y si la configuración trae su RUN. Sin Docker se omite.
 */
@SpringBootTest
@Import(PostgresDePrueba.class)
@Testcontainers(disabledWithoutDocker = true)
class AdministradorInicialTest {

	private static final String URL_ACTIVACION = "https://alertacuida.ejemplo.cl/activar";

	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private CrearAdministradorInicial crearAdministrador;

	/** Un servidor recién instalado: sin cuentas ni auditoría. */
	@BeforeEach
	void servidorVacio() {
		jdbc.sql("DELETE FROM registro_auditoria").update();
		jdbc.sql("DELETE FROM usuario").update();
	}

	/** Con el RUN configurado se crea un administrador pendiente, con el RUN cifrado y el enlace al dashboard. */
	@Test
	void conElRunConfiguradoSeCreaUnAdministradorPendiente() {
		Optional<String> enlace = alArrancar("12.345.678-5", "Ana Pérez", "ana@muni.cl").crear();

		assertThat(enlace).hasValueSatisfying(url -> assertThat(url).startsWith(URL_ACTIVACION + "#"));
		assertThat(jdbc.sql("SELECT codigo_rol FROM usuario").query(String.class).single()).isEqualTo("ADMINISTRADOR");
		assertThat(jdbc.sql("SELECT hash_contrasena FROM usuario").query(String.class).optional()).isEmpty();
		assertThat(jdbc.sql("SELECT rut FROM usuario").query(String.class).single())
				.startsWith("v1:").doesNotContain("12345678");
	}

	/** Al reiniciar el servidor, el administrador ya existe y no se crea otro. */
	@Test
	void alReiniciarNoSeCreaOtro() {
		alArrancar("12.345.678-5", "Ana Pérez", "ana@muni.cl").crear();

		assertThat(alArrancar("11.111.111-1", "Luis", "luis@muni.cl").crear()).isEmpty();
		assertThat(jdbc.sql("SELECT count(*) FROM usuario").query(Long.class).single()).isEqualTo(1);
	}

	/** Sin RUN configurado (desarrollo y pruebas) no hace nada. */
	@Test
	void sinRunConfiguradoNoHaceNada() {
		assertThat(alArrancar("", "", "").crear()).isEmpty();
		assertThat(alArrancar(null, null, null).crear()).isEmpty();
		assertThat(crearAdministrador.crearSiFalta("12.345.678-5", "Ana", "ana@muni.cl")).isPresent();
	}

	/** Con el RUN pero sin nombre o correo, el arranque se detiene diciendo qué falta. */
	@Test
	void conElRunPeroSinNombreOCorreoSeDetieneElArranque() {
		assertThatThrownBy(() -> alArrancar("12.345.678-5", "Ana", " ").crear())
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("ADMIN_INICIAL_CORREO");
		assertThatThrownBy(() -> alArrancar("12.345.678-5", null, "ana@muni.cl").crear())
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("ADMIN_INICIAL_NOMBRE");
	}

	/** @return el componente de arranque con esa configuración, como lo armaría Spring */
	private AdministradorInicialAlArrancar alArrancar(String run, String nombre, String correo) {
		return new AdministradorInicialAlArrancar(crearAdministrador,
				new PropiedadesAdministradorInicial(run, nombre, correo), URL_ACTIVACION);
	}

}
