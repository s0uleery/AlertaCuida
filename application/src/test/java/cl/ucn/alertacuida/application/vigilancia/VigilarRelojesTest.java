package cl.ucn.alertacuida.application.vigilancia;

import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.application.calendario.ReglaEnrutamiento;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.soporte.AlertasEnMemoria;
import cl.ucn.alertacuida.application.vigilancia.modelo.LoteEstado;
import cl.ucn.alertacuida.application.vigilancia.modelo.ParametrosVigilancia;
import cl.ucn.alertacuida.application.vigilancia.modelo.RelojVigilado;
import cl.ucn.alertacuida.application.vigilancia.puerto.RelojesVigilados;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import cl.ucn.alertacuida.domain.calendario.CalendarioHabil;
import cl.ucn.alertacuida.domain.calendario.DiaSemana;
import cl.ucn.alertacuida.domain.calendario.TramoHabil;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Reglas de ausencia, sin base de datos. Cada caso sale de lo decidido el 08-11-2026:
 * <ul>
 * <li>Silencio de datos: 4 horas sin sincronizar, gravedad media.</li>
 * <li>Batería baja: 2 sincronizaciones seguidas bajo 20 %, gravedad leve.</li>
 * <li>Reloj no puesto: 2 sincronizaciones seguidas del mismo día, entre las 08:00 y las 22:00 de
 * Chile, gravedad leve.</li>
 * <li>Las tres se cierran solas al volver a la normalidad y no se repiten mientras sigan abiertas.</li>
 * <li>Si una persona la cerró, no se crea otra hasta que el reloj se normalice y el problema vuelva.</li>
 * <li>Las leves creadas fuera del horario hábil quedan pendientes para el día hábil.</li>
 * </ul>
 */
class VigilarRelojesTest {

	private static final LocalDate HOY = LocalDate.of(2026, 11, 9);
	private static final Instant MEDIODIA = chile(HOY, 12, 0);
	private static final UUID VINCULO = UUID.randomUUID();
	private static final String SILENCIO = TipoEvento.SILENCIO_DATOS + "|" + VINCULO;
	private static final String BATERIA = TipoEvento.BATERIA_BAJA + "|" + VINCULO;
	private static final String NO_PUESTO = TipoEvento.RELOJ_NO_PUESTO + "|" + VINCULO;
	private static final ParametrosVigilancia PARAMETROS = new ParametrosVigilancia(Duration.ofHours(4), 20, 8, 22);
	/** Hábil el lunes de las pruebas, de 08:30 a 17:30. */
	private static final ReglaEnrutamiento ENRUTAMIENTO = new ReglaEnrutamiento(CalendarioHabil.de(
			Map.of(DiaSemana.LUNES, new TramoHabil(LocalTime.of(8, 30), LocalTime.of(17, 30))), List.of()));

	private final AlertasEnMemoria alertas = new AlertasEnMemoria();
	private final List<RelojVigilado> relojes = new ArrayList<>();
	/** Sincronizaciones que devuelve la búsqueda de lotes posteriores a una alerta. */
	private final List<LoteEstado> historial = new ArrayList<>();

	// ---------- Silencio de datos ----------

	/** A las 4 horas justas ya hay silencio, y es de gravedad media. */
	@Test
	void cuatroHorasSinSincronizarCreanSilencioMedio() {
		reloj(lote(MEDIODIA.minus(Duration.ofHours(4)), 80, true));

		vigilar(MEDIODIA);

		assertThat(alertas.tecnicas).containsExactly(SILENCIO);
		assertThat(alertas.gravedades.get(SILENCIO)).isEqualTo(Severidad.MEDIA);
	}

	/** Un minuto antes de las 4 horas todavía no hay silencio. */
	@Test
	void antesDeLasCuatroHorasNoHaySilencio() {
		reloj(lote(MEDIODIA.minus(Duration.ofMinutes(239)), 80, true));

		vigilar(MEDIODIA);

		assertThat(alertas.tecnicas).isEmpty();
	}

	/** Un reloj que nunca sincronizó se mide desde que se vinculó. */
	@Test
	void relojQueNuncaSincronizoSeMideDesdeElVinculo() {
		relojes.add(new RelojVigilado(VINCULO, MEDIODIA.minus(Duration.ofHours(4)), List.of()));

		vigilar(MEDIODIA);

		assertThat(alertas.tecnicas).containsExactly(SILENCIO);
	}

	/** Un reloj recién vinculado, sin sincronizaciones, todavía tiene plazo. */
	@Test
	void relojRecienVinculadoNoEstaEnSilencio() {
		relojes.add(new RelojVigilado(VINCULO, MEDIODIA.minus(Duration.ofHours(1)), List.of()));

		vigilar(MEDIODIA);

		assertThat(alertas.tecnicas).isEmpty();
	}

	/** Cuando vuelven los datos, el silencio abierto se cierra solo. */
	@Test
	void volverASincronizarCierraElSilencio() {
		reloj(lote(MEDIODIA.minus(Duration.ofHours(5)), 80, true));
		vigilar(MEDIODIA);
		relojes.clear();
		reloj(lote(MEDIODIA.plus(Duration.ofMinutes(10)), 80, true));

		vigilar(MEDIODIA.plus(Duration.ofMinutes(15)));

		assertThat(alertas.tecnicas).isEmpty();
		assertThat(alertas.cerradas).containsExactly(SILENCIO);
	}

	// ---------- Batería baja ----------

	/** Dos sincronizaciones seguidas bajo 20 % crean una alerta leve. */
	@Test
	void dosLotesBajoVeinteCreanBateriaBajaLeve() {
		reloj(lote(horas(1), 19, true), lote(horas(3), 19, true));

		vigilar(MEDIODIA);

		assertThat(alertas.tecnicas).containsExactly(BATERIA);
		assertThat(alertas.gravedades.get(BATERIA)).isEqualTo(Severidad.LEVE);
	}

	/** Una sola sincronización baja no demuestra que el estado persista. */
	@Test
	void unSoloLoteBajoNoAlerta() {
		reloj(lote(horas(1), 10, true));

		vigilar(MEDIODIA);

		assertThat(alertas.tecnicas).isEmpty();
	}

	/** El anterior estaba en 20 %, que no es "bajo 20": no son dos seguidas. */
	@Test
	void veinteNoEsBateriaBaja() {
		reloj(lote(horas(1), 19, true), lote(horas(3), 20, true));

		vigilar(MEDIODIA);

		assertThat(alertas.tecnicas).isEmpty();
	}

	/** Si un lote no informó la batería no se puede decir que siga baja. */
	@Test
	void bateriaSinInformarNoCuenta() {
		reloj(lote(horas(1), null, true), lote(horas(3), 10, true));

		vigilar(MEDIODIA);

		assertThat(alertas.tecnicas).isEmpty();
	}

	/** Con la batería de vuelta en 20 %, la alerta abierta se cierra sola. */
	@Test
	void recuperarLaBateriaCierraLaAlerta() {
		reloj(lote(horas(1), 19, true), lote(horas(3), 19, true));
		vigilar(MEDIODIA);
		relojes.clear();
		reloj(lote(horas(0), 20, true), lote(horas(1), 19, true));

		vigilar(MEDIODIA);

		assertThat(alertas.tecnicas).isEmpty();
		assertThat(alertas.cerradas).containsExactly(BATERIA);
	}

	/** Mientras siga baja, la alerta sigue abierta. */
	@Test
	void bateriaQueSigueBajaNoCierraLaAlerta() {
		reloj(lote(horas(1), 19, true), lote(horas(3), 19, true));
		vigilar(MEDIODIA);

		vigilar(MEDIODIA.plus(Duration.ofMinutes(5)));

		assertThat(alertas.tecnicas).containsExactly(BATERIA);
		assertThat(alertas.cerradas).isEmpty();
	}

	// ---------- Reloj no puesto ----------

	/** Dos sincronizaciones de día informando que no está puesto crean una alerta leve. */
	@Test
	void dosLotesDeDiaSinPonerCreanAlertaLeve() {
		reloj(lote(chile(HOY, 11, 0), 80, false), lote(chile(HOY, 9, 0), 80, false));

		vigilar(MEDIODIA);

		assertThat(alertas.tecnicas).containsExactly(NO_PUESTO);
		assertThat(alertas.gravedades.get(NO_PUESTO)).isEqualTo(Severidad.LEVE);
	}

	/** Una sola sincronización sin poner no alcanza. */
	@Test
	void unSoloLoteSinPonerNoAlerta() {
		reloj(lote(chile(HOY, 11, 0), 80, false), lote(chile(HOY, 9, 0), 80, true));

		vigilar(MEDIODIA);

		assertThat(alertas.tecnicas).isEmpty();
	}

	/** De noche es normal sacárselo para cargarlo. */
	@Test
	void deNocheNoAlerta() {
		Instant madrugada = chile(HOY, 3, 0);
		reloj(lote(chile(HOY, 1, 0), 80, false), lote(chile(HOY.minusDays(1), 23, 0), 80, false));

		vigilar(madrugada);

		assertThat(alertas.tecnicas).isEmpty();
	}

	/** Las 08:00 ya cuentan como día. */
	@Test
	void lasOchoEnPuntoYaSonDeDia() {
		reloj(lote(chile(HOY, 10, 0), 80, false), lote(chile(HOY, 8, 0), 80, false));

		vigilar(MEDIODIA);

		assertThat(alertas.tecnicas).containsExactly(NO_PUESTO);
	}

	/** Un lote de antes de las 08:00 viene de la noche: no suma. */
	@Test
	void unLoteDeAntesDeLasOchoNoCuenta() {
		reloj(lote(chile(HOY, 10, 0), 80, false), lote(chile(HOY, 7, 59), 80, false));

		vigilar(MEDIODIA);

		assertThat(alertas.tecnicas).isEmpty();
	}

	/** Las 21:59 todavía cuentan como día. */
	@Test
	void lasDiezMenosUnMinutoTodaviaSonDeDia() {
		reloj(lote(chile(HOY, 21, 59), 80, false), lote(chile(HOY, 19, 59), 80, false));

		vigilar(chile(HOY, 22, 1));

		assertThat(alertas.tecnicas).containsExactly(NO_PUESTO);
	}

	/** Las 22:00 ya son de noche. */
	@Test
	void lasDiezEnPuntoYaSonDeNoche() {
		reloj(lote(chile(HOY, 22, 0), 80, false), lote(chile(HOY, 20, 0), 80, false));

		vigilar(chile(HOY, 22, 1));

		assertThat(alertas.tecnicas).isEmpty();
	}

	/** Entre la tarde de ayer y la mañana de hoy pasó la noche: no son seguidas de un mismo día. */
	@Test
	void lotesDeDiasDistintosNoCuentan() {
		reloj(lote(chile(HOY, 8, 30), 80, false), lote(chile(HOY.minusDays(1), 21, 0), 80, false));

		vigilar(chile(HOY, 9, 0));

		assertThat(alertas.tecnicas).isEmpty();
	}

	/** Si el reloj dejó de informar si está puesto, no se puede afirmar que no lo esté. */
	@Test
	void puestoSinInformarNoCuenta() {
		reloj(lote(chile(HOY, 11, 0), 80, null), lote(chile(HOY, 9, 0), 80, false));

		vigilar(MEDIODIA);

		assertThat(alertas.tecnicas).isEmpty();
	}

	/** Al volver a ponérselo, la alerta abierta se cierra sola. */
	@Test
	void ponerseElRelojCierraLaAlerta() {
		reloj(lote(chile(HOY, 11, 0), 80, false), lote(chile(HOY, 9, 0), 80, false));
		vigilar(MEDIODIA);
		relojes.clear();
		reloj(lote(MEDIODIA, 80, true), lote(chile(HOY, 11, 0), 80, false));

		vigilar(MEDIODIA.plus(Duration.ofMinutes(5)));

		assertThat(alertas.tecnicas).isEmpty();
		assertThat(alertas.cerradas).containsExactly(NO_PUESTO);
	}

	/** Si anoche quedó sin poner, la alerta abierta sigue abierta: nadie ha dicho que se lo puso. */
	@Test
	void deNocheSinPonerNoCierraLaAlerta() {
		reloj(lote(chile(HOY, 21, 0), 80, false), lote(chile(HOY, 19, 0), 80, false));
		vigilar(chile(HOY, 21, 30));
		relojes.clear();
		reloj(lote(chile(HOY, 23, 0), 80, false), lote(chile(HOY, 21, 0), 80, false));

		vigilar(chile(HOY, 23, 30));

		assertThat(alertas.tecnicas).containsExactly(NO_PUESTO);
		assertThat(alertas.cerradas).isEmpty();
	}

	// ---------- Cierre a mano ----------

	/** Si una persona la cerró y la batería sigue baja, no se crea otra. */
	@Test
	void cerradaAManoConElProblemaVigenteNoSeRepite() {
		alertas.cerradasPorPersona.put(BATERIA, horas(5));
		reloj(lote(horas(1), 19, true), lote(horas(3), 19, true));
		historial.add(lote(horas(3), 19, true));
		historial.add(lote(horas(1), 19, true));

		vigilar(MEDIODIA);

		assertThat(alertas.tecnicas).isEmpty();
	}

	/** Si la batería se recuperó después del cierre y volvió a bajar, es un problema nuevo. */
	@Test
	void trasNormalizarseElProblemaNuevoSeAlerta() {
		alertas.cerradasPorPersona.put(BATERIA, horas(8));
		reloj(lote(horas(1), 19, true), lote(horas(3), 19, true));
		historial.add(lote(horas(6), 90, true));
		historial.add(lote(horas(3), 19, true));
		historial.add(lote(horas(1), 19, true));

		vigilar(MEDIODIA);

		assertThat(alertas.tecnicas).containsExactly(BATERIA);
	}

	/** Un silencio cerrado a mano no se repite mientras el reloj siga sin sincronizar. */
	@Test
	void silencioCerradoAManoSinNuevosDatosNoSeRepite() {
		alertas.cerradasPorPersona.put(SILENCIO, horas(1));
		reloj(lote(horas(6), 80, true));

		vigilar(MEDIODIA);

		assertThat(alertas.tecnicas).isEmpty();
	}

	/** Reloj no puesto cerrado a mano: las sincronizaciones sin ponérselo no lo normalizan. */
	@Test
	void relojNoPuestoCerradoAManoNoSeRepite() {
		alertas.cerradasPorPersona.put(NO_PUESTO, chile(HOY, 8, 30));
		reloj(lote(chile(HOY, 11, 0), 80, false), lote(chile(HOY, 9, 0), 80, false));
		historial.add(lote(chile(HOY, 9, 0), 80, false));
		historial.add(lote(chile(HOY, 11, 0), 80, false));

		vigilar(MEDIODIA);

		assertThat(alertas.tecnicas).isEmpty();
	}

	// ---------- Día hábil ----------

	/** Una leve creada de madrugada queda pendiente para el día hábil. */
	@Test
	void unaLeveFueraDeHorarioEsperaAlDiaHabil() {
		Instant madrugada = chile(HOY, 3, 0);
		reloj(lote(chile(HOY, 2, 0), 19, true), lote(chile(HOY, 0, 0), 19, true));

		vigilar(madrugada);

		assertThat(alertas.esperas.get(BATERIA)).isTrue();
	}

	/** Una leve en horario hábil no queda pendiente. */
	@Test
	void unaLeveEnHorarioNoEspera() {
		reloj(lote(horas(1), 19, true), lote(horas(3), 19, true));

		vigilar(MEDIODIA);

		assertThat(alertas.esperas.get(BATERIA)).isFalse();
	}

	/** El silencio es media: nunca queda pendiente, aunque sea de madrugada. */
	@Test
	void elSilencioNuncaEsperaAlDiaHabil() {
		Instant madrugada = chile(HOY, 3, 0);
		reloj(lote(madrugada.minus(Duration.ofHours(5)), 80, true));

		vigilar(madrugada);

		assertThat(alertas.esperas.get(SILENCIO)).isFalse();
	}

	// ---------- Sin repetir y sin detenerse ----------

	/** Varias pasadas del vigilante dejan una sola alerta abierta de cada tipo. */
	@Test
	void variasPasadasNoRepitenLaAlerta() {
		reloj(lote(horas(5), 19, false), lote(horas(7), 19, false));

		vigilar(MEDIODIA);
		vigilar(MEDIODIA.plus(Duration.ofMinutes(5)));
		vigilar(MEDIODIA.plus(Duration.ofMinutes(10)));

		assertThat(alertas.tecnicas).containsExactlyInAnyOrder(SILENCIO, BATERIA);
	}

	/** Un reloj con todo normal no genera nada. */
	@Test
	void relojNormalNoGeneraNada() {
		reloj(lote(horas(1), 80, true), lote(horas(3), 75, true));

		vigilar(MEDIODIA);

		assertThat(alertas.tecnicas).isEmpty();
		assertThat(alertas.cerradas).isEmpty();
	}

	/** Si falla la revisión de un reloj, los demás se revisan igual. */
	@Test
	void unaFallaNoDetieneAlRestoDeLosRelojes() {
		relojes.add(new RelojVigilado(UUID.randomUUID(), MEDIODIA.minus(Duration.ofDays(1)), List.of()));
		relojes.add(new RelojVigilado(VINCULO, MEDIODIA.minus(Duration.ofDays(1)), List.of()));
		Transacciones fallaLaPrimera = new Transacciones() {
			private boolean primera = true;

			@Override
			public <T> T ejecutar(Supplier<T> trabajo) {
				if (primera) {
					primera = false;
					throw new IllegalStateException("falla de prueba");
				}
				return trabajo.get();
			}
		};

		vigilante(MEDIODIA, fallaLaPrimera).vigilar();

		assertThat(alertas.tecnicas).containsExactly(SILENCIO);
	}

	// ---------- Ayudas ----------

	/** @return ese momento del día, en hora de Chile */
	private static Instant chile(LocalDate dia, int hora, int minuto) {
		return dia.atTime(hora, minuto).atZone(ZonaHoraria.CHILE).toInstant();
	}

	/** @return tantas horas antes del mediodía */
	private static Instant horas(int antes) {
		return MEDIODIA.minus(Duration.ofHours(antes));
	}

	/** @return una sincronización con esos datos */
	private static LoteEstado lote(Instant recepcion, Integer bateria, Boolean puesto) {
		return new LoteEstado(recepcion, bateria, puesto);
	}

	/** Agrega el reloj vigilado, vinculado desde ayer, con sus lotes (el más reciente primero). */
	private void reloj(LoteEstado... lotes) {
		relojes.add(new RelojVigilado(VINCULO, MEDIODIA.minus(Duration.ofDays(1)), List.of(lotes)));
	}

	/** Corre una pasada del vigilante a esa hora. */
	private void vigilar(Instant ahora) {
		vigilante(ahora, new Transacciones() {
			@Override
			public <T> T ejecutar(Supplier<T> trabajo) {
				return trabajo.get();
			}
		}).vigilar();
	}

	/** @return el vigilante con el reloj fijo en esa hora */
	private VigilarRelojes vigilante(Instant ahora, Transacciones transacciones) {
		RelojesVigilados consulta = new RelojesVigilados() {
			@Override
			public List<RelojVigilado> vigentes(int lotes) {
				return List.copyOf(relojes);
			}

			@Override
			public List<LoteEstado> lotesDesde(UUID idAsignacion, Instant desde) {
				return historial.stream().filter(lote -> lote.recepcion().isAfter(desde)).toList();
			}
		};
		return new VigilarRelojes(consulta, alertas, ReglasAusencia.todas(PARAMETROS), ENRUTAMIENTO, transacciones,
				Clock.fixed(ahora, ZoneOffset.UTC));
	}

}
