package cl.ucn.alertacuida.application.alerta;

import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.application.alerta.modelo.AlertaRecibida;
import cl.ucn.alertacuida.application.alerta.modelo.Discrepancia;
import cl.ucn.alertacuida.application.alerta.modelo.LecturaEvidencia;
import cl.ucn.alertacuida.application.clinica.modelo.AjustesClinicos;
import cl.ucn.alertacuida.application.clinica.modelo.Modulos;
import cl.ucn.alertacuida.application.clinica.modelo.PerfilClinico;
import cl.ucn.alertacuida.application.clinica.modelo.UmbralVigente;
import cl.ucn.alertacuida.application.clinica.modelo.ZonaGuardada;
import cl.ucn.alertacuida.domain.alerta.CodigoDiscrepancia;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import cl.ucn.alertacuida.domain.clinica.Umbral;
import cl.ucn.alertacuida.domain.clinica.ZonaSegura;
import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Comparación de una alerta con la configuración vigente, sin consultar nada. */
class RevisionDiscrepanciasTest {

	private static final String VIGENTE = "9f2c4e1a7b3d5e60";
	private static final Instant AHORA = Instant.parse("2026-11-05T15:00:00Z");
	/** FC normal de 50 a 110; grave bajo 40 o sobre 130. */
	private static final Umbral FC = Umbral.de(TipoLectura.FC, numero(40), numero(50), numero(110), numero(130));
	/** SpO2 normal desde 92; grave bajo 88. */
	private static final Umbral SPO2 = Umbral.de(TipoLectura.SPO2, numero(88), numero(92), null, null);

	/** La tendencia está configurada como MEDIA; la versión y el perfil no se consultan en estas pruebas. */
	private final RevisionDiscrepancias revision = new RevisionDiscrepancias(null, null, Severidad.MEDIA);

	/** Una alerta que calza en todo no tiene discrepancias. */
	@Test
	void unaAlertaQueCalzaNoTieneDiscrepancias() {
		assertThat(revisar(alerta(TipoEvento.FC, Severidad.GRAVE, lectura(TipoLectura.FC, 140)), perfil(todos(), null)))
				.isEmpty();
	}

	/** El reloj evaluó con otra versión: discrepancia con ambas versiones en el detalle. */
	@Test
	void otraVersionEsDiscrepancia() {
		AlertaRecibida conOtraVersion = new AlertaRecibida(UUID.randomUUID(), TipoEvento.CAIDA, Severidad.MEDIA,
				"vieja", AHORA, AHORA, false, null, null);

		List<Discrepancia> encontradas = revisar(conOtraVersion, perfil(todos(), null));

		assertThat(encontradas).extracting(Discrepancia::codigo).containsExactly(CodigoDiscrepancia.VERSION_DISTINTA);
		assertThat(encontradas.getFirst().detalle()).contains("vieja", VIGENTE);
	}

	/** Una alerta de un módulo apagado es discrepancia; FC_SPO2 basta con que uno de los dos esté apagado. */
	@Test
	void moduloApagadoEsDiscrepancia() {
		Modulos sinTendenciaNiSpo2 = new Modulos(true, true, false, true, false, false, true);

		assertThat(codigos(alerta(TipoEvento.TENDENCIA, Severidad.MEDIA), perfil(sinTendenciaNiSpo2, null)))
				.containsExactly(CodigoDiscrepancia.MODULO_APAGADO);
		assertThat(codigos(alerta(TipoEvento.FC_SPO2, Severidad.GRAVE), perfil(sinTendenciaNiSpo2, null)))
				.containsExactly(CodigoDiscrepancia.MODULO_APAGADO);
		assertThat(codigos(alerta(TipoEvento.ZONA_SEGURA, Severidad.MEDIA), perfil(sinTendenciaNiSpo2, null)))
				.contains(CodigoDiscrepancia.MODULO_APAGADO);
		assertThat(codigos(alerta(TipoEvento.CAIDA, Severidad.MEDIA), perfil(sinTendenciaNiSpo2, null))).isEmpty();
	}

	/** La tendencia y la zona deben llegar con la gravedad configurada; FC y SpO2 no se revisan (Anexo B). */
	@Test
	void gravedadDistintaEnTendenciaYZona() {
		ZonaGuardada zonaGrave = new ZonaGuardada(ZonaSegura.de(numero(0), numero(0), 200, Severidad.GRAVE), AHORA);

		assertThat(codigos(alerta(TipoEvento.TENDENCIA, Severidad.GRAVE), perfil(todos(), null)))
				.containsExactly(CodigoDiscrepancia.GRAVEDAD_DISTINTA);
		assertThat(codigos(alerta(TipoEvento.ZONA_SEGURA, Severidad.MEDIA), perfil(todos(), zonaGrave)))
				.containsExactly(CodigoDiscrepancia.GRAVEDAD_DISTINTA);
		assertThat(codigos(alerta(TipoEvento.ZONA_SEGURA, Severidad.GRAVE), perfil(todos(), zonaGrave))).isEmpty();
		assertThat(codigos(alerta(TipoEvento.FC, Severidad.LEVE), perfil(todos(), null))).isEmpty();
	}

	/** FC: alguna lectura más allá de un límite grave justifica la alerta; justo en el límite no. */
	@Test
	void lecturasDeFcDentroDeLosLimitesGraves() {
		PerfilClinico perfil = perfil(todos(), null);

		List<Discrepancia> enRango = revisar(alerta(TipoEvento.FC, Severidad.GRAVE, lectura(TipoLectura.FC, 130),
				lectura(TipoLectura.FC, 40)), perfil);
		assertThat(enRango).extracting(Discrepancia::codigo).containsExactly(CodigoDiscrepancia.LECTURAS_EN_RANGO);
		assertThat(enRango.getFirst().detalle()).contains("FC 130", "FC 40", "40 y 130");
		assertThat(codigos(alerta(TipoEvento.FC, Severidad.GRAVE, lectura(TipoLectura.FC, 130.5)), perfil)).isEmpty();
		assertThat(codigos(alerta(TipoEvento.FC, Severidad.GRAVE, lectura(TipoLectura.FC, 39)), perfil)).isEmpty();
	}

	/** SpO2: solo cuenta el límite grave bajo, y solo las lecturas de SpO2. */
	@Test
	void lecturasDeSpo2() {
		PerfilClinico perfil = perfil(todos(), null);

		assertThat(codigos(alerta(TipoEvento.SPO2, Severidad.GRAVE, lectura(TipoLectura.SPO2, 88),
				lectura(TipoLectura.FC, 200)), perfil)).containsExactly(CodigoDiscrepancia.LECTURAS_EN_RANGO);
		assertThat(codigos(alerta(TipoEvento.SPO2, Severidad.GRAVE, lectura(TipoLectura.SPO2, 87)), perfil)).isEmpty();
	}

	/** Tendencia y FC_SPO2: basta una lectura fuera del rango normal; los límites normales son normales. */
	@Test
	void lecturasDeTendenciaYFcSpo2() {
		PerfilClinico perfil = perfil(todos(), null);

		assertThat(codigos(alerta(TipoEvento.TENDENCIA, Severidad.MEDIA, lectura(TipoLectura.FC, 110),
				lectura(TipoLectura.SPO2, 92)), perfil)).containsExactly(CodigoDiscrepancia.LECTURAS_EN_RANGO);
		assertThat(codigos(alerta(TipoEvento.TENDENCIA, Severidad.MEDIA, lectura(TipoLectura.FC, 111)), perfil))
				.isEmpty();
		assertThat(codigos(alerta(TipoEvento.FC_SPO2, Severidad.GRAVE, lectura(TipoLectura.FC, 80),
				lectura(TipoLectura.SPO2, 91)), perfil)).isEmpty();
	}

	/** Sin lecturas en la evidencia, o en tipos que no se evalúan con lecturas, no se marca nada. */
	@Test
	void sinLecturasNoSeMarca() {
		PerfilClinico perfil = perfil(todos(), null);

		assertThat(codigos(alerta(TipoEvento.FC, Severidad.GRAVE), perfil)).isEmpty();
		assertThat(codigos(alerta(TipoEvento.CAIDA, Severidad.GRAVE, lectura(TipoLectura.FC, 80)), perfil)).isEmpty();
	}

	/** @return las discrepancias contra lo vigente */
	private List<Discrepancia> revisar(AlertaRecibida alerta, PerfilClinico perfil) {
		return revision.comparar(alerta, perfil, VIGENTE);
	}

	/** @return solo los códigos de las discrepancias */
	private List<CodigoDiscrepancia> codigos(AlertaRecibida alerta, PerfilClinico perfil) {
		return revisar(alerta, perfil).stream().map(Discrepancia::codigo).toList();
	}

	/** @return una alerta evaluada con la versión vigente, con esas lecturas en la evidencia */
	private static AlertaRecibida alerta(TipoEvento tipo, Severidad severidad, LecturaEvidencia... lecturas) {
		return new AlertaRecibida(UUID.randomUUID(), tipo, severidad, VIGENTE, AHORA, AHORA, false, null, null,
				List.of(lecturas));
	}

	/** @return el perfil con esos módulos, los umbrales de prueba y esa zona (o ninguna) */
	private static PerfilClinico perfil(Modulos modulos, ZonaGuardada zona) {
		return new PerfilClinico(new AjustesClinicos(modulos, true, false, null, null), AHORA, 5,
				new UmbralVigente(FC, false, null), new UmbralVigente(SPO2, false, null), zona);
	}

	/** @return todos los módulos activos */
	private static Modulos todos() {
		return new Modulos(true, true, true, true, true, true, true);
	}

	private static LecturaEvidencia lectura(TipoLectura tipo, double valor) {
		return new LecturaEvidencia(tipo, valor);
	}

	private static BigDecimal numero(int valor) {
		return BigDecimal.valueOf(valor);
	}

}
