package cl.ucn.alertacuida.adapters.seguridad.cifrado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.adapters.seguridad.PropiedadesSeguridad;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** Cifrado AES-256-GCM de los datos personales, sin base de datos ni Spring. */
class CifradoCamposTest {

	/** La de las pruebas (app/src/test/resources/config). */
	private static final String LLAVE = "uQNcQfIFTgL2//81x0SJ2dUxa3RPMjFD8/VANyWh5DY=";
	/** La del perfil dev (application-dev.properties). */
	private static final String OTRA_LLAVE = "pWX6Z0qepuUfO6uj2EJcqeCsZvILfiF/7rwH/MBDzqI=";

	private final CifradoCampos cifrado = conLlave(LLAVE);

	/** Lo cifrado se descifra igual, con tildes y ñ, y no contiene el dato original. */
	@Test
	void cifraYDescifraSinPerderTildes() {
		String guardado = cifrado.cifrar("Muñoz Peña", ColumnaCifrada.PACIENTE_APELLIDOS);

		assertThat(guardado).startsWith("v1:").doesNotContain("Muñoz");
		assertThat(cifrado.descifrar(guardado, ColumnaCifrada.PACIENTE_APELLIDOS)).isEqualTo("Muñoz Peña");
	}

	/** El mismo dato queda distinto cada vez: en la base no se nota que dos apellidos son iguales. */
	@Test
	void elMismoDatoQuedaDistintoCadaVez() {
		assertThat(cifrado.cifrar("Rojas", ColumnaCifrada.PACIENTE_APELLIDOS))
				.isNotEqualTo(cifrado.cifrar("Rojas", ColumnaCifrada.PACIENTE_APELLIDOS));
	}

	/** Null queda null en los dos sentidos (columnas opcionales). */
	@Test
	void nullQuedaNull() {
		assertThat(cifrado.cifrar(null, ColumnaCifrada.PACIENTE_DIRECCION)).isNull();
		assertThat(cifrado.descifrar(null, ColumnaCifrada.PACIENTE_DIRECCION)).isNull();
	}

	/** Un valor copiado a otra columna no se descifra. */
	@Test
	void unValorDeOtraColumnaNoSeDescifra() {
		String rut = cifrado.cifrar("12345678-5", ColumnaCifrada.PACIENTE_RUT);

		assertThatThrownBy(() -> cifrado.descifrar(rut, ColumnaCifrada.PACIENTE_NOMBRES))
				.isInstanceOf(IllegalStateException.class);
	}

	/** Un valor alterado, cifrado con otra llave o sin cifrar no se acepta. */
	@Test
	void valorAlteradoOtraLlaveOSinCifrarSeRechazan() {
		String guardado = cifrado.cifrar("María", ColumnaCifrada.PACIENTE_NOMBRES);
		byte[] bytes = Base64.getDecoder().decode(guardado.substring(3));
		bytes[bytes.length - 1] ^= 1;
		String alterado = "v1:" + Base64.getEncoder().encodeToString(bytes);

		assertThatThrownBy(() -> cifrado.descifrar(alterado, ColumnaCifrada.PACIENTE_NOMBRES))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> conLlave(OTRA_LLAVE).descifrar(guardado, ColumnaCifrada.PACIENTE_NOMBRES))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> cifrado.descifrar("María", ColumnaCifrada.PACIENTE_NOMBRES))
				.isInstanceOf(IllegalStateException.class);
	}

	/** El RUN del administrador de prueba (R__datos_de_ejemplo.sql) se descifra con la llave del perfil dev. */
	@Test
	void elRunDelAdministradorDePruebaSeDescifraConLaLlaveDeDesarrollo() {
		CifradoCampos desarrollo = conLlave(OTRA_LLAVE);

		assertThat(desarrollo.descifrar("v1:9RDK5ztxjd10uEbkHMPfeH3Q4wKkK0qPVewUS8V1xBbKNaQmTxM=",
				ColumnaCifrada.USUARIO_RUT)).isEqualTo("11111111-1");
	}

	/** Una llave que no es base64 o no tiene 32 bytes impide arrancar. */
	@Test
	void llaveInvalidaImpideArrancar() {
		assertThatThrownBy(() -> conLlave("no es base64!")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> conLlave(Base64.getEncoder().encodeToString(new byte[16])))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
	}

	/** @return el cifrado con esa llave y el resto de la configuración en valores cualquiera */
	private static CifradoCampos conLlave(String llave) {
		return new CifradoCampos(new PropiedadesSeguridad("clave-run-de-prueba", "x".repeat(32), llave,
				Duration.ofMinutes(15),
				new PropiedadesSeguridad.Sesion(Duration.ofHours(10), Duration.ofMinutes(15), Duration.ofSeconds(10),
						true),
				new PropiedadesSeguridad.Bloqueo(5, Duration.ofMinutes(15)),
				new PropiedadesSeguridad.Activacion("http://localhost/activar", Duration.ofHours(48))));
	}

}
