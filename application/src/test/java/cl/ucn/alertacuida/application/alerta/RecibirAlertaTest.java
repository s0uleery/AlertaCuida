package cl.ucn.alertacuida.application.alerta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.alerta.modelo.AlertaRecibida;
import cl.ucn.alertacuida.application.alerta.modelo.Discrepancia;
import cl.ucn.alertacuida.application.alerta.modelo.LecturaEvidencia;
import cl.ucn.alertacuida.application.alerta.modelo.LimiteAlertas;
import cl.ucn.alertacuida.application.alerta.modelo.ResultadoAlerta;
import cl.ucn.alertacuida.application.asignacion.AutenticarReloj;
import cl.ucn.alertacuida.application.asignacion.modelo.RelojAutenticado;
import cl.ucn.alertacuida.application.calendario.ReglaEnrutamiento;
import cl.ucn.alertacuida.application.clinica.PerfilClinicoPaciente;
import cl.ucn.alertacuida.application.clinica.modelo.ValoresBase;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.paciente.modelo.DatosPaciente;
import cl.ucn.alertacuida.application.soporte.AlertasEnMemoria;
import cl.ucn.alertacuida.application.soporte.AsignacionesEnMemoria;
import cl.ucn.alertacuida.application.soporte.PacientesEnMemoria;
import cl.ucn.alertacuida.application.soporte.PerfilesClinicosEnMemoria;
import cl.ucn.alertacuida.domain.alerta.CodigoDiscrepancia;
import cl.ucn.alertacuida.domain.alerta.EstadoContacto;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import cl.ucn.alertacuida.domain.calendario.CalendarioHabil;
import cl.ucn.alertacuida.domain.calendario.DiaSemana;
import cl.ucn.alertacuida.domain.calendario.TramoHabil;
import cl.ucn.alertacuida.domain.clinica.Umbral;
import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Alertas del reloj, cancelaciones, agravamiento, discrepancias, exceso de alertas y autenticación
 * del reloj, sin base de datos.
 */
class RecibirAlertaTest {

	private static final Instant AHORA = Instant.parse("2026-10-25T15:00:00Z");
	/** La versión que calcula el servidor en estas pruebas; las alertas de ejemplo llegan con ella. */
	private static final String VIGENTE = "v1";
	private static final Umbral FC_BASE = Umbral.de(TipoLectura.FC, BigDecimal.valueOf(40), BigDecimal.valueOf(50),
			BigDecimal.valueOf(110), BigDecimal.valueOf(130));
	private static final Umbral SPO2_BASE = Umbral.de(TipoLectura.SPO2, BigDecimal.valueOf(88),
			BigDecimal.valueOf(92), null, null);

	private final Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
	private final AlertasEnMemoria alertas = new AlertasEnMemoria();
	private final PacientesEnMemoria pacientes = new PacientesEnMemoria();
	private final UUID maria = pacientes.crear(new DatosPaciente("12345678-5", "María", "Rojas", null, null, null,
			null, null, null, LocalDate.of(2026, 1, 10)), "h");
	private final RelojAutenticado relojDeMaria = new RelojAutenticado(UUID.randomUUID(), UUID.randomUUID(), maria,
			AHORA.minusSeconds(86_400));
	private final RecibirAlerta recibir = conLimite(new LimiteAlertas(10, Duration.ofHours(1)));

	/** La alerta queda RECIBIDA; reenviarla devuelve la misma, sin duplicarla. */
	@Test
	void unaAlertaReenviadaNoSeDuplica() {
		AlertaRecibida alerta = alerta(UUID.randomUUID(), Severidad.GRAVE, false);

		ResultadoAlerta primera = recibir.recibir(relojDeMaria, alerta);
		ResultadoAlerta reenvio = recibir.recibir(relojDeMaria, alerta);

		assertThat(primera.nueva()).isTrue();
		assertThat(primera.respuesta().estado().estado()).isEqualTo(EstadoContacto.RECIBIDA);
		assertThat(primera.respuesta().estado().detenerEscalamiento()).isFalse();
		assertThat(reenvio.nueva()).isFalse();
		assertThat(reenvio.respuesta().idAlerta()).isEqualTo(primera.respuesta().idAlerta());
		assertThat(alertas.filas).hasSize(1);
	}

	/** Solo la leve espera al día hábil: una media marcada así se guarda sin la marca, no se rechaza. */
	@Test
	void soloLaLeveEsperaAlDiaHabil() {
		ResultadoAlerta leve = recibir.recibir(relojDeMaria, alerta(UUID.randomUUID(), Severidad.LEVE, true));
		ResultadoAlerta media = recibir.recibir(relojDeMaria, alerta(UUID.randomUUID(), Severidad.MEDIA, true));

		assertThat(leve.respuesta().estado().estado()).isEqualTo(EstadoContacto.PENDIENTE_DIA_HABIL);
		assertThat(media.respuesta().estado().estado()).isEqualTo(EstadoContacto.RECIBIDA);
	}

	/** El botón de ayuda siempre es leve: si llega con otra gravedad se guarda como leve, sin rechazarlo. */
	@Test
	void elBotonDeAyudaSeGuardaComoLeve() {
		UUID eventId = UUID.randomUUID();
		AlertaRecibida boton = new AlertaRecibida(eventId, TipoEvento.BOTON_AYUDA, Severidad.GRAVE, "v1", AHORA,
				AHORA, true, null, null);

		ResultadoAlerta resultado = recibir.recibir(relojDeMaria, boton);

		assertThat(alertas.filas.get(eventId).alerta().severidad()).isEqualTo(Severidad.LEVE);
		assertThat(resultado.respuesta().estado().estado()).isEqualTo(EstadoContacto.PENDIENTE_DIA_HABIL);
	}

	/** Una cancelación queda CANCELADA y detiene el escalamiento; el mismo eventId no puede ser alerta. */
	@Test
	void laCancelacionQuedaCanceladaYNoSeConfundeConUnaAlerta() {
		UUID eventId = UUID.randomUUID();
		AlertaRecibida cancelacion = new AlertaRecibida(eventId, TipoEvento.CAIDA, Severidad.MEDIA, "v1",
				AHORA.minusSeconds(40), null, false, AHORA.minusSeconds(20), null);

		ResultadoAlerta resultado = recibir.registrarCancelacion(relojDeMaria, cancelacion);

		assertThat(resultado.respuesta().estado().estado()).isEqualTo(EstadoContacto.CANCELADA);
		assertThat(resultado.respuesta().estado().detenerEscalamiento()).isTrue();
		assertThatThrownBy(() -> recibir.recibir(relojDeMaria, alerta(eventId, Severidad.MEDIA, false)))
				.isInstanceOf(Conflicto.class);
	}

	/** El reloj no envía silencios de datos, y toda alerta trae la configuración con que se evaluó. */
	@Test
	void tipoDelServidorOSinConfiguracionSeRechazan() {
		AlertaRecibida silencio = new AlertaRecibida(UUID.randomUUID(), TipoEvento.SILENCIO_DATOS, Severidad.MEDIA,
				"v1", AHORA, AHORA, false, null, null);
		AlertaRecibida sinConfiguracion = new AlertaRecibida(UUID.randomUUID(), TipoEvento.FC, Severidad.MEDIA, " ",
				AHORA, AHORA, false, null, null);

		assertThatThrownBy(() -> recibir.recibir(relojDeMaria, silencio)).isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> recibir.recibir(relojDeMaria, sinConfiguracion))
				.isInstanceOf(SolicitudRechazada.class);
	}

	/** Un reloj solo ve el estado de sus alertas: la de otro reloj responde "no encontrada". */
	@Test
	void unRelojSoloConsultaSusAlertas() {
		UUID eventId = UUID.randomUUID();
		recibir.recibir(relojDeMaria, alerta(eventId, Severidad.GRAVE, false));
		RelojAutenticado otro = new RelojAutenticado(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AHORA);

		assertThat(recibir.estado(relojDeMaria, eventId).eventId()).isEqualTo(eventId);
		assertThatThrownBy(() -> recibir.estado(otro, eventId)).isInstanceOf(NoEncontrado.class);
		assertThatThrownBy(() -> recibir.recibir(otro, alerta(eventId, Severidad.GRAVE, false)))
				.isInstanceOf(Conflicto.class);
	}

	/** El reloj se reconoce solo con el token de un vínculo vigente: al cerrarlo, el token deja de servir. */
	@Test
	void elTokenSoloSirveMientrasElVinculoEsteVigente() {
		AsignacionesEnMemoria asignaciones = new AsignacionesEnMemoria();
		SecretosUnSoloUso secretos = new SecretosUnSoloUso() {
			@Override
			public String nuevo() {
				return "token";
			}

			@Override
			public String hash(String secreto) {
				return "h-" + secreto;
			}
		};
		AutenticarReloj autenticar = new AutenticarReloj(asignaciones, secretos);
		UUID vinculo = asignaciones.abrir(UUID.randomUUID(), UUID.randomUUID(), AHORA, "h-token");

		assertThat(autenticar.autenticar("token")).get().extracting(RelojAutenticado::idAsignacion)
				.isEqualTo(vinculo);
		assertThat(autenticar.autenticar("otro")).isEmpty();
		assertThat(autenticar.autenticar(" ")).isEmpty();
		asignaciones.cerrar(vinculo, AHORA.plusSeconds(60));
		assertThat(autenticar.autenticar("token")).isEmpty();
	}

	/** Una alerta que no calza se acepta igual y queda con sus discrepancias; una que calza, sin ninguna. */
	@Test
	void lasDiscrepanciasSeGuardanSinRechazarLaAlerta() {
		UUID calza = UUID.randomUUID();
		UUID noCalza = UUID.randomUUID();
		recibir.recibir(relojDeMaria, alerta(calza, Severidad.MEDIA, false));
		ResultadoAlerta resultado = recibir.recibir(relojDeMaria, new AlertaRecibida(noCalza, TipoEvento.FC,
				Severidad.GRAVE, "vieja", AHORA.minusSeconds(40), AHORA.minusSeconds(10), false, null, null,
				List.of(new LecturaEvidencia(TipoLectura.FC, 95))));

		assertThat(resultado.nueva()).isTrue();
		assertThat(alertas.discrepancias.get(alertas.filas.get(calza).guardada().idAlerta())).isEmpty();
		assertThat(alertas.discrepancias.get(resultado.respuesta().idAlerta())).extracting(Discrepancia::codigo)
				.containsExactly(CodigoDiscrepancia.VERSION_DISTINTA, CodigoDiscrepancia.LECTURAS_EN_RANGO);
	}

	/** Con 9 alertas en la hora no pasa nada; la 10.ª crea una sola alerta técnica, aunque sigan llegando. */
	@Test
	void elExcesoDeAlertasCreaUnaSolaAlertaTecnica() {
		for (int i = 0; i < 9; i++) {
			recibir.recibir(relojDeMaria, alerta(UUID.randomUUID(), Severidad.MEDIA, false));
		}
		assertThat(alertas.tecnicas).isEmpty();

		recibir.recibir(relojDeMaria, alerta(UUID.randomUUID(), Severidad.MEDIA, false));
		recibir.recibir(relojDeMaria, alerta(UUID.randomUUID(), Severidad.MEDIA, false));

		assertThat(alertas.filas).hasSize(11);
		assertThat(alertas.tecnicas).containsExactly("EXCESO_ALERTAS|" + relojDeMaria.idAsignacion());
	}

	/** Es leve: un domingo, fuera del horario hábil, queda pendiente para el día hábil. */
	@Test
	void elExcesoFueraDeHorarioEsperaAlDiaHabil() {
		for (int i = 0; i < 10; i++) {
			recibir.recibir(relojDeMaria, alerta(UUID.randomUUID(), Severidad.MEDIA, false));
		}

		assertThat(alertas.esperas.get("EXCESO_ALERTAS|" + relojDeMaria.idAsignacion())).isTrue();
	}

	/** En horario hábil no queda pendiente. */
	@Test
	void elExcesoEnHorarioNoEspera() {
		RecibirAlerta conDomingoHabil = conCalendario(new LimiteAlertas(10, Duration.ofHours(1)), DiaSemana.DOMINGO);
		for (int i = 0; i < 10; i++) {
			conDomingoHabil.recibir(relojDeMaria, alerta(UUID.randomUUID(), Severidad.MEDIA, false));
		}

		assertThat(alertas.esperas.get("EXCESO_ALERTAS|" + relojDeMaria.idAsignacion())).isFalse();
	}

	/** Las cancelaciones no cuentan para el exceso: el paciente pudo cancelar muchas falsas alarmas. */
	@Test
	void lasCancelacionesNoCuentanParaElExceso() {
		for (int i = 0; i < 11; i++) {
			recibir.registrarCancelacion(relojDeMaria, new AlertaRecibida(UUID.randomUUID(), TipoEvento.CAIDA,
					Severidad.MEDIA, VIGENTE, AHORA.minusSeconds(40), null, false, AHORA.minusSeconds(20), null));
		}

		assertThat(alertas.tecnicas).isEmpty();
	}

	/** Una caída media pasa a grave una sola vez: repetirlo no cambia la hora ni la inmovilidad. */
	@Test
	void unaCaidaSeAgravaUnaSolaVez() {
		UUID caida = UUID.randomUUID();
		recibir.recibir(relojDeMaria, alerta(caida, Severidad.MEDIA, false));

		assertThat(recibir.agravar(relojDeMaria, caida, AHORA, 120).severidadActual()).isEqualTo(Severidad.GRAVE);
		recibir.agravar(relojDeMaria, caida, AHORA.plusSeconds(60), 999);

		assertThat(alertas.filas.get(caida).agravamiento()).isEqualTo(AHORA);
		assertThat(alertas.filas.get(caida).inmovilidad()).isEqualTo(120);
	}

	/** Solo se agrava una caída propia, que no llegó grave ni se canceló. */
	@Test
	void soloSeAgravaUnaCaidaPropiaNoGraveNiCancelada() {
		UUID grave = UUID.randomUUID();
		UUID fc = UUID.randomUUID();
		UUID cancelada = UUID.randomUUID();
		recibir.recibir(relojDeMaria, alerta(grave, Severidad.GRAVE, false));
		recibir.recibir(relojDeMaria, new AlertaRecibida(fc, TipoEvento.FC, Severidad.MEDIA, VIGENTE, AHORA, AHORA,
				false, null, null));
		recibir.registrarCancelacion(relojDeMaria, new AlertaRecibida(cancelada, TipoEvento.CAIDA, Severidad.MEDIA,
				VIGENTE, AHORA, null, false, AHORA, null));
		RelojAutenticado otro = new RelojAutenticado(UUID.randomUUID(), UUID.randomUUID(), maria, AHORA);

		assertThatThrownBy(() -> recibir.agravar(relojDeMaria, grave, AHORA, null)).isInstanceOf(Conflicto.class);
		assertThatThrownBy(() -> recibir.agravar(relojDeMaria, fc, AHORA, null)).isInstanceOf(Conflicto.class);
		assertThatThrownBy(() -> recibir.agravar(relojDeMaria, cancelada, AHORA, null)).isInstanceOf(Conflicto.class);
		assertThatThrownBy(() -> recibir.agravar(otro, grave, AHORA, null)).isInstanceOf(NoEncontrado.class);
		assertThatThrownBy(() -> recibir.agravar(relojDeMaria, UUID.randomUUID(), AHORA, null))
				.isInstanceOf(NoEncontrado.class);
		assertThatThrownBy(() -> recibir.agravar(relojDeMaria, grave, AHORA, -1))
				.isInstanceOf(SolicitudRechazada.class);
	}

	/**
	 * @return el caso de uso con ese límite, la paciente con los valores base y la versión vigente
	 *         {@value #VIGENTE}
	 */
	private RecibirAlerta conLimite(LimiteAlertas limite) {
		return conCalendario(limite, DiaSemana.LUNES);
	}

	/** @return el caso de uso con ese límite, y con ese único día hábil, de 08:30 a 17:30 */
	private RecibirAlerta conCalendario(LimiteAlertas limite, DiaSemana diaHabil) {
		ReglaEnrutamiento enrutamiento = new ReglaEnrutamiento(CalendarioHabil.de(
				Map.of(diaHabil, new TramoHabil(LocalTime.of(8, 30), LocalTime.of(17, 30))), List.of()));
		PerfilClinicoPaciente perfiles = new PerfilClinicoPaciente(new PerfilesClinicosEnMemoria(), pacientes,
				new ValoresBase(FC_BASE, SPO2_BASE, 5), (usuario, accion, entidad, id, ip, fecha, detalle) -> { },
				Supplier::get, reloj);
		Transacciones enTransaccion = Supplier::get;
		RevisionDiscrepancias revision = new RevisionDiscrepancias(idPaciente -> VIGENTE, perfiles, Severidad.MEDIA);
		return new RecibirAlerta(alertas, revision, limite, enrutamiento, enTransaccion, reloj);
	}

	/** @return una caída enviada al terminar la ventana de 30 segundos, evaluada con la versión vigente */
	private static AlertaRecibida alerta(UUID eventId, Severidad severidad, boolean esperaDiaHabil) {
		return new AlertaRecibida(eventId, TipoEvento.CAIDA, severidad, VIGENTE, AHORA.minusSeconds(40),
				AHORA.minusSeconds(10), esperaDiaHabil, null, "{\"segundosInmovilidad\":60}");
	}

}
