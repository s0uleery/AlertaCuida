package cl.ucn.alertacuida.application.clinica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.clinica.modelo.AjustesClinicos;
import cl.ucn.alertacuida.application.clinica.modelo.Modulos;
import cl.ucn.alertacuida.application.clinica.modelo.PerfilClinico;
import cl.ucn.alertacuida.application.clinica.modelo.ValoresBase;
import cl.ucn.alertacuida.application.clinica.modelo.ZonaGuardada;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.paciente.modelo.DatosPaciente;
import cl.ucn.alertacuida.application.soporte.PacientesEnMemoria;
import cl.ucn.alertacuida.application.soporte.PerfilesClinicosEnMemoria;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.clinica.Umbral;
import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Perfil clínico de un paciente, sin base de datos. */
class PerfilClinicoPacienteTest {

	private static final UUID COORDINADOR = UUID.randomUUID();
	private static final Umbral FC_BASE = Umbral.de(TipoLectura.FC, numero(40), numero(50), numero(110),
			numero(130));
	private static final Umbral SPO2_BASE = Umbral.de(TipoLectura.SPO2, numero(88), numero(92), null, null);

	private final PacientesEnMemoria pacientes = new PacientesEnMemoria();
	private final PerfilesClinicosEnMemoria guardados = new PerfilesClinicosEnMemoria();
	private final List<String> auditoria = new ArrayList<>();
	private PerfilClinicoPaciente perfil;
	private UUID maria;

	/** El caso de uso con dobles en memoria, valores base de 5 minutos y una paciente ya registrada. */
	@BeforeEach
	void preparar() {
		Clock reloj = Clock.fixed(Instant.parse("2026-10-25T15:00:00Z"), ZoneOffset.UTC);
		RegistroAuditoria registro = (usuario, accion, entidad, id, ip, fecha) -> auditoria.add(accion + " " + entidad);
		Transacciones enTransaccion = Supplier::get;
		perfil = new PerfilClinicoPaciente(guardados, pacientes, new ValoresBase(FC_BASE, SPO2_BASE, 5), registro,
				enTransaccion, reloj);
		maria = pacientes.crear(new DatosPaciente("12345678-5", "María", "Rojas", null, null, null, null, null, null,
				LocalDate.of(2026, 1, 10)), "h");
	}

	/** Sin configuración rigen los módulos por defecto y los valores base. */
	@Test
	void sinConfiguracionRigenLosValoresBase() {
		PerfilClinico actual = perfil.consultar(maria);

		assertThat(actual.ajustes()).isEqualTo(AjustesClinicos.POR_DEFECTO);
		assertThat(actual.ajustesDesde()).isNull();
		assertThat(actual.minutosEspera()).isEqualTo(5);
		assertThat(actual.fc().umbral()).isEqualTo(FC_BASE);
		assertThat(actual.fc().propio()).isFalse();
		assertThat(actual.spo2().umbral()).isEqualTo(SPO2_BASE);
	}

	/** Cambiar los ajustes agrega una fila con auditoría; repetirlos (aunque cambie el motivo) no agrega otra. */
	@Test
	void cambiarAjustesAgregaUnaFilaSoloSiCambiaAlgo() {
		AjustesClinicos conGps = new AjustesClinicos(Modulos.POR_DEFECTO, true, false, 8, "Sale a caminar sola");

		assertThat(perfil.cambiarAjustes(maria, conGps, COORDINADOR, "ip")).isTrue();
		assertThat(perfil.cambiarAjustes(maria, new AjustesClinicos(Modulos.POR_DEFECTO, true, false, 8, "otro"),
				COORDINADOR, "ip")).isFalse();

		PerfilClinico actual = perfil.consultar(maria);
		assertThat(actual.ajustes().gpsActivo()).isTrue();
		assertThat(actual.ajustes().motivo()).isEqualTo("Sale a caminar sola");
		assertThat(actual.minutosEspera()).isEqualTo(8);
		assertThat(actual.ajustesDesde()).isEqualTo(Instant.parse("2026-10-25T15:00:00Z"));
		assertThat(auditoria).containsExactly("CREAR CONFIGURACION");
	}

	/** Los minutos de espera van de 1 a 60 y el motivo tiene un largo máximo. */
	@Test
	void rechazaEsperaFueraDeRangoYMotivoMuyLargo() {
		assertThatThrownBy(() -> perfil.cambiarAjustes(maria, new AjustesClinicos(Modulos.POR_DEFECTO, false, false,
				0, null), COORDINADOR, "ip")).isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> perfil.cambiarAjustes(maria, new AjustesClinicos(Modulos.POR_DEFECTO, false, false,
				61, null), COORDINADOR, "ip")).isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> perfil.cambiarAjustes(maria, new AjustesClinicos(Modulos.POR_DEFECTO, false, true,
				null, "x".repeat(501)), COORDINADOR, "ip")).isInstanceOf(SolicitudRechazada.class);
		assertThat(guardados.filas()).isZero();
	}

	/** Un umbral propio rige hasta que se vuelve al base, que agrega una fila vacía sin borrar el historial. */
	@Test
	void unUmbralPropioRigeHastaVolverAlBase() {
		perfil.ajustarUmbral(maria, TipoLectura.SPO2, numero(85), numero(90), null, null, COORDINADOR, "ip");

		assertThat(perfil.consultar(maria).spo2().propio()).isTrue();
		assertThat(perfil.consultar(maria).spo2().umbral().normalMin()).isEqualByComparingTo("90");
		assertThat(perfil.consultar(maria).fc().propio()).isFalse();

		assertThat(perfil.volverAlUmbralBase(maria, TipoLectura.SPO2, COORDINADOR, "ip")).isTrue();
		assertThat(perfil.volverAlUmbralBase(maria, TipoLectura.SPO2, COORDINADOR, "ip")).isFalse();
		assertThat(perfil.consultar(maria).spo2().umbral()).isEqualTo(SPO2_BASE);
		assertThat(guardados.filas()).isEqualTo(2);
		assertThat(auditoria).containsExactly("CREAR UMBRAL", "CREAR UMBRAL");
	}

	/** Un umbral desordenado se rechaza con el mensaje del dominio; uno igual al vigente no agrega fila. */
	@Test
	void rechazaUmbralDesordenadoYNoRepiteElVigente() {
		assertThatThrownBy(() -> perfil.ajustarUmbral(maria, TipoLectura.FC, numero(60), numero(50), numero(110),
				numero(130), COORDINADOR, "ip"))
				.isInstanceOf(SolicitudRechazada.class)
				.hasMessageContaining("orden");
		perfil.ajustarUmbral(maria, TipoLectura.FC, numero(35), numero(45), numero(100), numero(120), COORDINADOR,
				"ip");

		assertThat(perfil.ajustarUmbral(maria, TipoLectura.FC, new BigDecimal("35.0"), numero(45), numero(100),
				numero(120), COORDINADOR, "ip")).isFalse();
		assertThat(guardados.filas()).isEqualTo(1);
	}

	/** La zona se guarda con historial y auditoría, media por defecto; una igual a la vigente no agrega fila. */
	@Test
	void laZonaSeGuardaMediaPorDefectoYNoSeRepite() {
		assertThat(perfil.definirZona(maria, new BigDecimal("-23.6509"), new BigDecimal("-70.3975"), 200, null,
				COORDINADOR, "ip")).isTrue();
		assertThat(perfil.definirZona(maria, new BigDecimal("-23.65090"), new BigDecimal("-70.3975"), 200,
				Severidad.MEDIA, COORDINADOR, "ip")).isFalse();

		ZonaGuardada zona = perfil.consultar(maria).zonaSegura();
		assertThat(zona.zona().severidad()).isEqualTo(Severidad.MEDIA);
		assertThat(zona.zona().radioMetros()).isEqualTo(200);
		assertThat(zona.vigenteDesde()).isEqualTo(Instant.parse("2026-10-25T15:00:00Z"));
		assertThat(auditoria).containsExactly("CREAR ZONA_SEGURA");
	}

	/** Un centro fuera del mapa o un radio fuera de rango se rechazan sin guardar nada. */
	@Test
	void rechazaZonaFueraDelMapaORadioFueraDeRango() {
		assertThatThrownBy(() -> perfil.definirZona(maria, new BigDecimal("91"), BigDecimal.ZERO, 200, null,
				COORDINADOR, "ip")).isInstanceOf(SolicitudRechazada.class).hasMessageContaining("latitud");
		assertThatThrownBy(() -> perfil.definirZona(maria, BigDecimal.ZERO, BigDecimal.ZERO, 10, null,
				COORDINADOR, "ip")).isInstanceOf(SolicitudRechazada.class).hasMessageContaining("radio");
		assertThat(guardados.filas()).isZero();
	}

	/** Activar la zona exige el GPS y una zona guardada; con ambos se activa. */
	@Test
	void activarLaZonaExigeGpsYUnaZonaGuardada() {
		Modulos conZona = new Modulos(true, true, true, true, true, true, true);

		assertThatThrownBy(() -> perfil.cambiarAjustes(maria, new AjustesClinicos(conZona, true, false, null, null),
				COORDINADOR, "ip")).isInstanceOf(SolicitudRechazada.class).hasMessageContaining("define la zona");
		perfil.definirZona(maria, BigDecimal.ZERO, BigDecimal.ZERO, 300, Severidad.GRAVE, COORDINADOR, "ip");
		assertThatThrownBy(() -> perfil.cambiarAjustes(maria, new AjustesClinicos(conZona, false, false, null, null),
				COORDINADOR, "ip")).isInstanceOf(SolicitudRechazada.class).hasMessageContaining("GPS");

		assertThat(perfil.cambiarAjustes(maria, new AjustesClinicos(conZona, true, false, null, null), COORDINADOR,
				"ip")).isTrue();
		assertThat(perfil.consultar(maria).ajustes().modulos().zonaSegura()).isTrue();
	}

	/** Con un paciente que no existe, todo responde "no encontrado". */
	@Test
	void pacienteInexistenteRespondeNoEncontrado() {
		UUID nadie = UUID.randomUUID();

		assertThatThrownBy(() -> perfil.consultar(nadie)).isInstanceOf(NoEncontrado.class);
		assertThatThrownBy(() -> perfil.volverAlUmbralBase(nadie, TipoLectura.FC, COORDINADOR, "ip"))
				.isInstanceOf(NoEncontrado.class);
		assertThatThrownBy(() -> perfil.definirZona(nadie, BigDecimal.ZERO, BigDecimal.ZERO, 200, null, COORDINADOR,
				"ip")).isInstanceOf(NoEncontrado.class);
	}

	/** @return el número como BigDecimal */
	private static BigDecimal numero(int valor) {
		return BigDecimal.valueOf(valor);
	}

}
