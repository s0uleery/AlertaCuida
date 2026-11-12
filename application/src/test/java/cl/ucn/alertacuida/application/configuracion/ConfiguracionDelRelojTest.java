package cl.ucn.alertacuida.application.configuracion;

import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.clinica.PerfilClinicoPaciente;
import cl.ucn.alertacuida.application.clinica.modelo.AjustesClinicos;
import cl.ucn.alertacuida.application.clinica.modelo.Modulos;
import cl.ucn.alertacuida.application.clinica.modelo.ValoresBase;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.configuracion.modelo.ConfiguracionReloj;
import cl.ucn.alertacuida.application.configuracion.modelo.ContactoConfigurado;
import cl.ucn.alertacuida.application.configuracion.modelo.ContenidoConfiguracion;
import cl.ucn.alertacuida.application.contacto.ContactosPaciente;
import cl.ucn.alertacuida.application.contacto.modelo.DatosContacto;
import cl.ucn.alertacuida.application.contacto.puerto.HashMedioContacto;
import cl.ucn.alertacuida.application.paciente.modelo.DatosPaciente;
import cl.ucn.alertacuida.application.soporte.ContactosEnMemoria;
import cl.ucn.alertacuida.application.soporte.PacientesEnMemoria;
import cl.ucn.alertacuida.application.soporte.PerfilesClinicosEnMemoria;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.calendario.CalendarioHabil;
import cl.ucn.alertacuida.domain.calendario.DiaSemana;
import cl.ucn.alertacuida.domain.calendario.Feriado;
import cl.ucn.alertacuida.domain.calendario.TramoHabil;
import cl.ucn.alertacuida.domain.clinica.Umbral;
import cl.ucn.alertacuida.domain.contacto.Canal;
import cl.ucn.alertacuida.domain.contacto.Correo;
import cl.ucn.alertacuida.domain.contacto.Horario;
import cl.ucn.alertacuida.domain.contacto.NumeroWhatsapp;
import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Configuración que descarga el reloj, sin base de datos. */
class ConfiguracionDelRelojTest {

	private static final UUID COORDINADOR = UUID.randomUUID();
	private static final int MINUTOS_TENDENCIA = 120;
	private static final Umbral FC_BASE = Umbral.de(TipoLectura.FC, numero(40), numero(50), numero(110),
			numero(130));
	private static final Umbral SPO2_BASE = Umbral.de(TipoLectura.SPO2, numero(88), numero(92), null, null);
	/** Feriados de cuatro años: solo viajan los del año en curso (2026) y el siguiente. */
	private static final CalendarioHabil CALENDARIO = CalendarioHabil.de(
			Map.of(DiaSemana.LUNES, new TramoHabil(LocalTime.of(8, 30), LocalTime.of(17, 30))),
			List.of(new Feriado(LocalDate.of(2025, 12, 25), "Navidad"),
					new Feriado(LocalDate.of(2026, 9, 18), "Independencia"),
					new Feriado(LocalDate.of(2027, 1, 1), "Año Nuevo"),
					new Feriado(LocalDate.of(2028, 1, 1), "Año Nuevo")));

	private final PacientesEnMemoria pacientes = new PacientesEnMemoria();
	private final PerfilesClinicosEnMemoria perfilesGuardados = new PerfilesClinicosEnMemoria();
	private final ContactosEnMemoria contactosGuardados = new ContactosEnMemoria();
	private PerfilClinicoPaciente perfiles;
	private ContactosPaciente contactos;
	private ConfiguracionDelReloj configuraciones;
	private UUID maria;

	/** Los casos de uso con dobles en memoria, el 25-10-2026, y una paciente sin ajustes ni contactos. */
	@BeforeEach
	void preparar() {
		Clock reloj = Clock.fixed(Instant.parse("2026-10-25T15:00:00Z"), ZoneOffset.UTC);
		RegistroAuditoria registro = (usuario, accion, entidad, id, ip, fecha) -> { };
		Transacciones enTransaccion = Supplier::get;
		HashMedioContacto hash = new HashMedioContacto() {
			@Override
			public String de(NumeroWhatsapp numero) {
				return "w:" + numero.valor();
			}

			@Override
			public String de(Correo correo) {
				return "c:" + correo.valor();
			}
		};
		perfiles = new PerfilClinicoPaciente(perfilesGuardados, pacientes, new ValoresBase(FC_BASE, SPO2_BASE, 5),
				registro, enTransaccion, reloj);
		contactos = new ContactosPaciente(contactosGuardados, pacientes, hash, registro, enTransaccion, id -> { },
				reloj);
		configuraciones = conReloj(reloj);
		maria = pacientes.crear(new DatosPaciente("12345678-5", "María", "Rojas", null, null, null, null, null, null,
				LocalDate.of(2026, 1, 10)), "h");
	}

	/** Sin ajustes viajan los módulos por defecto, los valores base, la matriz provisional y el calendario. */
	@Test
	void sinAjustesViajanLosValoresBaseYElCalendario() {
		ContenidoConfiguracion contenido = configuraciones.armar(maria).contenido();

		assertThat(contenido.modulos()).isEqualTo(Modulos.POR_DEFECTO);
		assertThat(contenido.gpsActivo()).isFalse();
		assertThat(contenido.fc()).isEqualTo(FC_BASE);
		assertThat(contenido.spo2()).isEqualTo(SPO2_BASE);
		assertThat(contenido.minutosEspera()).isEqualTo(5);
		assertThat(contenido.rondas()).isEqualTo(2);
		assertThat(contenido.versionMatriz()).isEqualTo("PROVISIONAL");
		assertThat(contenido.minutosTendencia()).isEqualTo(MINUTOS_TENDENCIA);
		assertThat(contenido.severidadTendencia()).isEqualTo(Severidad.MEDIA);
		assertThat(contenido.contactos()).isEmpty();
		assertThat(contenido.horarioHabil()).containsOnlyKeys(DiaSemana.LUNES);
		assertThat(contenido.feriados()).containsExactly(LocalDate.of(2026, 9, 18), LocalDate.of(2027, 1, 1));
	}

	/** Solo viajan los activos con un canal usable, por prioridad: WhatsApp con opt-in o correo. */
	@Test
	void soloViajanLosContactosQuePuedenRecibirAvisos() {
		UUID ana = contactos.agregar(maria, contacto("Ana", "9 1234 5678", null, "08:00", "20:00"), COORDINADOR, "ip");
		contactos.agregar(maria, contacto("Luis sin opt-in", "9 8765 4321", null, null, null), COORDINADOR, "ip");
		UUID seguridad = contactos.agregar(maria, contacto("Seguridad", null, "turno@muni.cl", null, null),
				COORDINADOR, "ip");
		UUID pedro = contactos.agregar(maria, contacto("Pedro", null, "pedro@mail.cl", null, null), COORDINADOR, "ip");
		contactos.registrarOptIn(maria, ana, true, COORDINADOR, "ip");
		contactos.desactivar(maria, pedro, COORDINADOR, "ip");

		List<ContactoConfigurado> lista = configuraciones.armar(maria).contenido().contactos();

		assertThat(lista).extracting(ContactoConfigurado::idContacto).containsExactly(ana, seguridad);
		assertThat(lista.getFirst().nombre()).isEqualTo("Ana");
		assertThat(lista.getFirst().prioridad()).isEqualTo(1);
		assertThat(lista.getFirst().horario()).isEqualTo(Horario.de(LocalTime.of(8, 0), LocalTime.of(20, 0)));
		assertThat(lista.getFirst().canales()).containsExactly(Canal.WHATSAPP);
		assertThat(lista.get(1).prioridad()).isEqualTo(3);
		assertThat(lista.get(1).canales()).containsExactly(Canal.CORREO);
	}

	/** La versión es la misma mientras nada cambie, y cambia con cualquier dato, aunque sea un horario. */
	@Test
	void laVersionCambiaConCualquierDato() {
		UUID ana = contactos.agregar(maria, contacto("Ana", null, "ana@mail.cl", null, null), COORDINADOR, "ip");
		ConfiguracionReloj primera = configuraciones.armar(maria);

		assertThat(primera.version()).matches("[0-9a-f]{16}");
		assertThat(configuraciones.armar(maria).version()).isEqualTo(primera.version());
		assertThat(configuraciones.de(maria)).isEqualTo(primera.version());

		contactos.editar(maria, ana, contacto("Ana", null, "ana@mail.cl", "09:00", "18:00"), COORDINADOR, "ip");
		String conHorario = configuraciones.de(maria);
		assertThat(conHorario).isNotEqualTo(primera.version());

		perfiles.cambiarAjustes(maria, new AjustesClinicos(Modulos.POR_DEFECTO, true, false, null, null), COORDINADOR,
				"ip");
		assertThat(configuraciones.de(maria)).isNotEqualTo(conHorario);
	}

	/** La zona viaja solo con el módulo activo; cambiarla mientras está apagada no cambia la versión. */
	@Test
	void laZonaViajaSoloConElModuloActivo() {
		perfiles.definirZona(maria, new BigDecimal("-23.65"), new BigDecimal("-70.4"), 250, null, COORDINADOR, "ip");
		String apagada = configuraciones.de(maria);
		assertThat(configuraciones.armar(maria).contenido().zonaSegura()).isNull();

		perfiles.definirZona(maria, new BigDecimal("-23.65"), new BigDecimal("-70.4"), 400, null, COORDINADOR, "ip");
		assertThat(configuraciones.de(maria)).isEqualTo(apagada);

		perfiles.cambiarAjustes(maria, new AjustesClinicos(new Modulos(true, true, true, true, true, true, true), true,
				false, null, null), COORDINADOR, "ip");
		ContenidoConfiguracion contenido = configuraciones.armar(maria).contenido();
		assertThat(contenido.zonaSegura().radioMetros()).isEqualTo(400);
		assertThat(contenido.zonaSegura().severidad()).isEqualTo(Severidad.MEDIA);
		assertThat(configuraciones.de(maria)).isNotEqualTo(apagada);
	}

	/** Cada contacto viaja con su tipo, para que el botón de ayuda avise primero a los vecinos. */
	@Test
	void cadaContactoViajaConSuTipo() {
		contactos.agregar(maria, contacto("Ana", null, "ana@mail.cl", null, null), COORDINADOR, "ip");

		assertThat(configuraciones.armar(maria).contenido().contactos().getFirst().tipo()).isEqualTo("VECINO");
	}

	/** Cambiar el tiempo o la gravedad de la regla de tendencia cambia la versión: el reloj debe descargarlos. */
	@Test
	void laVersionCambiaConLaReglaDeTendencia() {
		Clock reloj = Clock.fixed(Instant.parse("2026-10-25T15:00:00Z"), ZoneOffset.UTC);
		ConfiguracionDelReloj conOtroTiempo = new ConfiguracionDelReloj(perfiles, contactos, CALENDARIO, "PROVISIONAL",
				MINUTOS_TENDENCIA + 30, Severidad.MEDIA, reloj);
		ConfiguracionDelReloj conOtraGravedad = new ConfiguracionDelReloj(perfiles, contactos, CALENDARIO,
				"PROVISIONAL", MINUTOS_TENDENCIA, Severidad.GRAVE, reloj);

		assertThat(conOtroTiempo.de(maria)).isNotEqualTo(configuraciones.de(maria));
		assertThat(conOtraGravedad.de(maria)).isNotEqualTo(configuraciones.de(maria));
	}

	/** Los feriados se cuentan por año calendario: la versión cambia el 1 de enero, no cuando pasa un feriado. */
	@Test
	void laVersionCambiaSoloAlEmpezarElAnio() {
		String octubre = configuraciones.de(maria);
		String diciembre = conReloj(Clock.fixed(Instant.parse("2026-12-31T12:00:00Z"), ZoneOffset.UTC)).de(maria);
		String enero = conReloj(Clock.fixed(Instant.parse("2027-01-01T12:00:00Z"), ZoneOffset.UTC)).de(maria);

		assertThat(diciembre).isEqualTo(octubre);
		assertThat(enero).isNotEqualTo(diciembre);
	}

	/** @return el caso de uso con la matriz PROVISIONAL y ese reloj */
	private ConfiguracionDelReloj conReloj(Clock reloj) {
		return new ConfiguracionDelReloj(perfiles, contactos, CALENDARIO, "PROVISIONAL", MINUTOS_TENDENCIA,
				Severidad.MEDIA, reloj);
	}

	/** @return un vecino, con horario si vienen las dos horas ("08:00") */
	private static DatosContacto contacto(String nombre, String whatsapp, String correo, String inicio, String fin) {
		return new DatosContacto(nombre, whatsapp, correo, "VECINO", null, false,
				inicio == null ? null : LocalTime.parse(inicio), fin == null ? null : LocalTime.parse(fin));
	}

	private static BigDecimal numero(int valor) {
		return BigDecimal.valueOf(valor);
	}

}
