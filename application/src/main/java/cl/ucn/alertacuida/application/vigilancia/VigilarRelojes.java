package cl.ucn.alertacuida.application.vigilancia;

import cl.ucn.alertacuida.application.alerta.puerto.Alertas;
import cl.ucn.alertacuida.application.calendario.ReglaEnrutamiento;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.vigilancia.modelo.RelojVigilado;
import cl.ucn.alertacuida.application.vigilancia.puerto.RelojesVigilados;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Vigilante de los relojes: aplica las reglas de ausencia a cada reloj vigente, crea las alertas
 * técnicas que correspondan y cierra las que ya no tienen motivo.
 */
public class VigilarRelojes {

	/** Sincronizaciones seguidas que se necesitan para decir que un estado persiste. */
	static final int LOTES_SEGUIDOS = 2;

	private static final System.Logger LOG = System.getLogger(VigilarRelojes.class.getName());

	private final RelojesVigilados relojes;
	private final Alertas alertas;
	private final List<ReglaAusencia> reglas;
	private final ReglaEnrutamiento enrutamiento;
	private final Transacciones transacciones;
	private final Clock reloj;

	/**
	 * Recibe lo que usa; lo arma ConfiguracionVigilancia.
	 *
	 * @param reglas       las reglas que se aplican a cada reloj
	 * @param enrutamiento decide si una alerta espera al día hábil
	 */
	public VigilarRelojes(RelojesVigilados relojes, Alertas alertas, List<ReglaAusencia> reglas,
			ReglaEnrutamiento enrutamiento, Transacciones transacciones, Clock reloj) {
		this.relojes = relojes;
		this.alertas = alertas;
		this.reglas = List.copyOf(reglas);
		this.enrutamiento = enrutamiento;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/**
	 * Revisa todos los relojes vigentes una vez. Cada reloj va en su propia transacción: si falla uno,
	 * se registra el error y se sigue con los demás.
	 */
	public void vigilar() {
		Instant ahora = reloj.instant();
		for (RelojVigilado vigilado : relojes.vigentes(LOTES_SEGUIDOS)) {
			try {
				transacciones.ejecutar(() -> {
					aplicarReglas(vigilado, ahora);
					return null;
				});
			} catch (RuntimeException falla) {
				LOG.log(System.Logger.Level.ERROR, "No se pudo revisar el vínculo " + vigilado.idAsignacion(), falla);
			}
		}
	}

	/** Crea o cierra la alerta técnica de cada regla, según lo que encuentre en el reloj. */
	private void aplicarReglas(RelojVigilado vigilado, Instant ahora) {
		for (ReglaAusencia regla : reglas) {
			switch (regla.evaluar(vigilado, ahora)) {
				case ACTIVA -> crearSiCorresponde(vigilado, regla, ahora);
				case NORMAL -> alertas.cerrarTecnicaAbierta(vigilado.idAsignacion(), regla.tipo(), ahora);
				case SIN_CAMBIO -> { }
			}
		}
	}

	/** Crea la alerta, salvo que una persona ya haya cerrado la de este mismo problema. */
	private void crearSiCorresponde(RelojVigilado vigilado, ReglaAusencia regla, Instant ahora) {
		if (!yaLaCerroUnaPersona(vigilado, regla)) {
			alertas.crearTecnicaSiNoHayAbierta(vigilado.idAsignacion(), regla.tipo(), regla.severidad(),
					regla.detalle(vigilado), enrutamiento.esperaDiaHabil(regla.severidad(), ahora), ahora);
		}
	}

	/**
	 * @return si la última alerta de esta regla la cerró una persona y, desde entonces, ninguna
	 *         sincronización demuestra que el reloj se normalizó: sigue siendo el mismo problema
	 */
	private boolean yaLaCerroUnaPersona(RelojVigilado vigilado, ReglaAusencia regla) {
		return alertas.ultimaCerradaPorPersona(vigilado.idAsignacion(), regla.tipo())
				.filter(creada -> relojes.lotesDesde(vigilado.idAsignacion(), creada).stream()
						.noneMatch(regla::normaliza))
				.isPresent();
	}

}
