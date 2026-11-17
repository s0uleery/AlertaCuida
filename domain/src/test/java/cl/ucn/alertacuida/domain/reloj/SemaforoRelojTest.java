package cl.ucn.alertacuida.domain.reloj;

import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Semáforo de cada reloj (decisiones del 13-11 y del 16-11-2026, ver la memoria del proyecto y docs/alertas.md).
 * Sale de los datos que el reloj ya envía, sin consultarlo. Umbrales iguales a los de las reglas de ausencia.
 * <ul>
 * <li>S1 Gris "Sin vincular" si nadie lo usa.</li>
 * <li>S2 Rojo "Sin señal" desde 4 horas sin sincronizar (desde el vínculo si nunca sincronizó), o con el plan de
 * datos vencido.</li>
 * <li>S3 Amarillo "Atención" si la última sincronización tiene batería bajo 20 %, o no puesto entre las 08:00 y
 * las 22:00 de Chile; si la configuración está pendiente; si hay una alerta técnica abierta; si pasaron más de
 * 2 h 30 min sin sincronizar; si su app es más antigua que la más nueva del programa; o si el plan vence en 30
 * días o menos.</li>
 * <li>S4 Verde "Funcionando" si nada de lo anterior.</li>
 * <li>S5 Trae todos los motivos que aplican, para mostrarlos al pasar el mouse.</li>
 * </ul>
 */
class SemaforoRelojTest {

	private static final UmbralesSemaforo UMBRALES = new UmbralesSemaforo(Duration.ofHours(4), 20, 8, 22,
			Duration.ofMinutes(150), 30);

	/** Lunes 9 de noviembre de 2026 a mediodía, hora de Chile. */
	private static final Instant AHORA = LocalDate.of(2026, 11, 9).atTime(12, 0).atZone(ZonaHoraria.CHILE)
			.toInstant();
	private static final LocalDate HOY = LocalDate.of(2026, 11, 9);

	/** S4: un reloj al día, sincronizado hace una hora, con todo en orden, está verde y sin motivos. */
	@Test
	void unRelojAlDiaEstaVerde() {
		EstadoSemaforo estado = evaluar(normal());

		assertThat(estado.color()).isEqualTo(ColorSemaforo.VERDE);
		assertThat(estado.motivos()).isEmpty();
	}

	/** S1: sin vínculo, gris. */
	@Test
	void sinVincularEstaGris() {
		EstadoSemaforo estado = evaluar(new DatosSemaforo(false, null, null, null, null, null, false, false, null,
				null));

		assertThat(estado.color()).isEqualTo(ColorSemaforo.GRIS);
		assertThat(estado.motivos()).containsExactly(MotivoSemaforo.SIN_VINCULAR);
	}

	/** S2: a las 4 horas justas ya no hay señal; un minuto antes es solo atraso. */
	@Test
	void cuatroHorasSinSincronizarEsRojo() {
		assertThat(evaluar(sincronizadoHace(Duration.ofHours(4))).color()).isEqualTo(ColorSemaforo.ROJO);
		assertThat(evaluar(sincronizadoHace(Duration.ofHours(4))).motivos()).containsExactly(MotivoSemaforo.SIN_SENAL);
		assertThat(evaluar(sincronizadoHace(Duration.ofMinutes(239))).color()).isEqualTo(ColorSemaforo.AMARILLO);
	}

	/** S2: si nunca sincronizó, se cuenta desde el vínculo. */
	@Test
	void sinSincronizarSeCuentaDesdeElVinculo() {
		DatosSemaforo recien = new DatosSemaforo(true, AHORA.minus(Duration.ofMinutes(30)), null, null, null, null,
				false, false, null, null);
		DatosSemaforo viejo = new DatosSemaforo(true, AHORA.minus(Duration.ofHours(5)), null, null, null, null,
				false, false, null, null);

		assertThat(evaluar(recien).color()).isEqualTo(ColorSemaforo.VERDE);
		assertThat(evaluar(viejo).motivos()).containsExactly(MotivoSemaforo.SIN_SENAL);
	}

	/** S3: más de 2 h 30 min sin sincronizar es atraso; justo 2 h 30 min, todavía no. */
	@Test
	void atrasoDesdeDosHorasYMedia() {
		assertThat(evaluar(sincronizadoHace(Duration.ofMinutes(150))).color()).isEqualTo(ColorSemaforo.VERDE);
		assertThat(evaluar(sincronizadoHace(Duration.ofMinutes(151))).motivos())
				.containsExactly(MotivoSemaforo.ATRASO);
	}

	/** S3: batería bajo 20 % es amarillo; 20 % no es bajo. */
	@Test
	void bateriaBajaEsAmarillo() {
		assertThat(evaluar(conBateria(19)).motivos()).containsExactly(MotivoSemaforo.BATERIA_BAJA);
		assertThat(evaluar(conBateria(20)).color()).isEqualTo(ColorSemaforo.VERDE);
	}

	/** S3: no puesto cuenta solo si la sincronización fue entre las 08:00 (incluida) y las 22:00 (excluida). */
	@Test
	void noPuestoSoloDeDia() {
		assertThat(noPuestoA(8, 0).motivos()).containsExactly(MotivoSemaforo.NO_PUESTO);
		assertThat(noPuestoA(7, 59).color()).isEqualTo(ColorSemaforo.VERDE);
		assertThat(noPuestoA(21, 59).motivos()).containsExactly(MotivoSemaforo.NO_PUESTO);
		assertThat(noPuestoA(22, 0).color()).isEqualTo(ColorSemaforo.VERDE);
	}

	/** S3: configuración pendiente y alerta técnica abierta son amarillo. */
	@Test
	void configuracionPendienteYAlertaTecnicaSonAmarillo() {
		DatosSemaforo n = normal();
		DatosSemaforo pendiente = new DatosSemaforo(true, n.inicioVinculo(), n.ultimaSincronizacion(), 80, true,
				"1.2.0", true, false, "1.2.0", null);
		DatosSemaforo conAlerta = new DatosSemaforo(true, n.inicioVinculo(), n.ultimaSincronizacion(), 80, true,
				"1.2.0", false, true, "1.2.0", null);

		assertThat(evaluar(pendiente).motivos()).containsExactly(MotivoSemaforo.CONFIGURACION_PENDIENTE);
		assertThat(evaluar(conAlerta).motivos()).containsExactly(MotivoSemaforo.ALERTA_TECNICA_ABIERTA);
	}

	/** S3: una app más antigua que la más nueva del programa es amarillo; se comparan los números, no el texto. */
	@Test
	void versionAntiguaEsAmarillo() {
		assertThat(evaluar(conVersion("1.2.0", "1.10.0")).motivos()).containsExactly(MotivoSemaforo.VERSION_ANTIGUA);
		assertThat(evaluar(conVersion("1.10.0", "1.10.0")).color()).isEqualTo(ColorSemaforo.VERDE);
		assertThat(evaluar(conVersion("1.10", "1.10.0")).color()).isEqualTo(ColorSemaforo.VERDE);
		assertThat(evaluar(conVersion(null, "1.10.0")).color()).isEqualTo(ColorSemaforo.VERDE);
	}

	/** S2 y S3: el plan vencido es rojo; si vence en 30 días o menos, amarillo; el día del vencimiento aún sirve. */
	@Test
	void planDeDatos() {
		assertThat(evaluar(conPlan(HOY.minusDays(1))).motivos()).containsExactly(MotivoSemaforo.PLAN_VENCIDO);
		assertThat(evaluar(conPlan(HOY.minusDays(1))).color()).isEqualTo(ColorSemaforo.ROJO);
		assertThat(evaluar(conPlan(HOY)).motivos()).containsExactly(MotivoSemaforo.PLAN_POR_VENCER);
		assertThat(evaluar(conPlan(HOY.plusDays(30))).motivos()).containsExactly(MotivoSemaforo.PLAN_POR_VENCER);
		assertThat(evaluar(conPlan(HOY.plusDays(31))).color()).isEqualTo(ColorSemaforo.VERDE);
	}

	/** S5: con varios problemas trae todos los motivos, y el rojo gana sobre el amarillo. */
	@Test
	void traeTodosLosMotivosYGanaElRojo() {
		DatosSemaforo varios = new DatosSemaforo(true, AHORA.minus(Duration.ofDays(2)),
				AHORA.minus(Duration.ofHours(5)), 10, true, "1.0.0", true, true, "1.1.0", HOY.plusDays(5));

		EstadoSemaforo estado = evaluar(varios);

		assertThat(estado.color()).isEqualTo(ColorSemaforo.ROJO);
		assertThat(estado.motivos()).containsExactlyInAnyOrder(MotivoSemaforo.SIN_SENAL, MotivoSemaforo.BATERIA_BAJA,
				MotivoSemaforo.CONFIGURACION_PENDIENTE, MotivoSemaforo.ALERTA_TECNICA_ABIERTA,
				MotivoSemaforo.VERSION_ANTIGUA, MotivoSemaforo.PLAN_POR_VENCER);
	}

	/** S1: sin vincular sigue gris, pero avisa si el plan de su SIM venció. */
	@Test
	void sinVincularAvisaElPlan() {
		EstadoSemaforo estado = evaluar(new DatosSemaforo(false, null, null, null, null, null, false, false, null,
				HOY.minusDays(3)));

		assertThat(estado.color()).isEqualTo(ColorSemaforo.GRIS);
		assertThat(estado.motivos()).containsExactly(MotivoSemaforo.SIN_VINCULAR, MotivoSemaforo.PLAN_VENCIDO);
	}

	/** @return el estado del reloj a mediodía del lunes */
	private static EstadoSemaforo evaluar(DatosSemaforo datos) {
		return SemaforoReloj.evaluar(datos, UMBRALES, AHORA);
	}

	/** @return un reloj vinculado hace un día, sincronizado hace una hora, puesto, con 80 % y al día */
	private static DatosSemaforo normal() {
		return sincronizadoHace(Duration.ofHours(1));
	}

	private static DatosSemaforo sincronizadoHace(Duration hace) {
		return new DatosSemaforo(true, AHORA.minus(Duration.ofDays(1)), AHORA.minus(hace), 80, true, "1.2.0", false,
				false, "1.2.0", null);
	}

	private static DatosSemaforo conBateria(int bateria) {
		DatosSemaforo n = normal();
		return new DatosSemaforo(true, n.inicioVinculo(), n.ultimaSincronizacion(), bateria, true, "1.2.0", false,
				false, "1.2.0", null);
	}

	/** @return el estado, 5 minutos después, de un reloj que informó no estar puesto a esa hora de Chile */
	private static EstadoSemaforo noPuestoA(int hora, int minuto) {
		Instant sincronizacion = HOY.atTime(hora, minuto).atZone(ZonaHoraria.CHILE).toInstant();
		DatosSemaforo datos = new DatosSemaforo(true, sincronizacion.minus(Duration.ofDays(1)), sincronizacion, 80,
				false, "1.2.0", false, false, "1.2.0", null);
		return SemaforoReloj.evaluar(datos, UMBRALES, sincronizacion.plus(Duration.ofMinutes(5)));
	}

	private static DatosSemaforo conVersion(String version, String masNueva) {
		DatosSemaforo n = normal();
		return new DatosSemaforo(true, n.inicioVinculo(), n.ultimaSincronizacion(), 80, true, version, false, false,
				masNueva, null);
	}

	private static DatosSemaforo conPlan(LocalDate vencimiento) {
		DatosSemaforo n = normal();
		return new DatosSemaforo(true, n.inicioVinculo(), n.ultimaSincronizacion(), 80, true, "1.2.0", false, false,
				"1.2.0", vencimiento);
	}

}
