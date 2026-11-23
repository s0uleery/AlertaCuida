package cl.ucn.alertacuida.application.alerta;

import cl.ucn.alertacuida.application.alerta.modelo.AlertaRecibida;
import cl.ucn.alertacuida.application.alerta.modelo.Discrepancia;
import cl.ucn.alertacuida.application.alerta.modelo.LecturaEvidencia;
import cl.ucn.alertacuida.application.clinica.PerfilClinicoPaciente;
import cl.ucn.alertacuida.application.clinica.modelo.Modulos;
import cl.ucn.alertacuida.application.clinica.modelo.PerfilClinico;
import cl.ucn.alertacuida.application.configuracion.VersionVigente;
import cl.ucn.alertacuida.domain.alerta.CodigoDiscrepancia;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.clinica.Umbral;
import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Compara una alerta del reloj con la configuración vigente al recibirla. Lo que no calza se
 * devuelve como discrepancia; la alerta se acepta igual, porque el servidor nunca descarta una.
 *
 * <p>Revisa cuatro cosas: la versión de la configuración, que el módulo esté activo, la gravedad
 * configurada (tendencia y zona segura) y que las lecturas de la evidencia superen los umbrales
 * del paciente. La gravedad de FC y SpO2 depende de la matriz del Anexo B, que aún no existe.
 */
public class RevisionDiscrepancias {

	private final VersionVigente versiones;
	private final PerfilClinicoPaciente perfiles;
	private final Severidad severidadTendencia;

	/**
	 * Recibe lo que usa; lo arma ConfiguracionApiReloj.
	 *
	 * @param versiones          la versión de configuración que calcula hoy el servidor
	 * @param perfiles           módulos, umbrales y zona vigentes del paciente
	 * @param severidadTendencia gravedad configurada para la tendencia (alertacuida.clinica.severidad-tendencia)
	 */
	public RevisionDiscrepancias(VersionVigente versiones, PerfilClinicoPaciente perfiles,
			Severidad severidadTendencia) {
		this.versiones = versiones;
		this.perfiles = perfiles;
		this.severidadTendencia = severidadTendencia;
	}

	/** @return lo que no calza entre la alerta y la configuración vigente del paciente; vacía si todo calza */
	public List<Discrepancia> revisar(UUID idPaciente, AlertaRecibida alerta) {
		return comparar(alerta, perfiles.consultar(idPaciente), versiones.de(idPaciente));
	}

	/**
	 * La comparación, sin consultar nada: recibe lo vigente ya armado.
	 *
	 * @param versionVigente la versión que calcula hoy el servidor
	 */
	List<Discrepancia> comparar(AlertaRecibida alerta, PerfilClinico perfil, String versionVigente) {
		List<Discrepancia> encontradas = new ArrayList<>();
		if (!versionVigente.equals(alerta.versionConfiguracion())) {
			encontradas.add(new Discrepancia(CodigoDiscrepancia.VERSION_DISTINTA, "El reloj usó la versión "
					+ alerta.versionConfiguracion() + "; la vigente es " + versionVigente));
		}
		if (moduloApagado(alerta, perfil.ajustes().modulos())) {
			encontradas.add(new Discrepancia(CodigoDiscrepancia.MODULO_APAGADO,
					"El módulo de " + alerta.tipo() + " está apagado para este paciente"));
		}
		Severidad esperada = severidadEsperada(alerta, perfil);
		if (esperada != null && esperada != alerta.severidad()) {
			encontradas.add(new Discrepancia(CodigoDiscrepancia.GRAVEDAD_DISTINTA,
					"Llegó " + alerta.severidad() + "; la configurada es " + esperada));
		}
		String lecturasEnRango = lecturasEnRango(alerta, perfil);
		if (lecturasEnRango != null) {
			encontradas.add(new Discrepancia(CodigoDiscrepancia.LECTURAS_EN_RANGO, lecturasEnRango));
		}
		return encontradas;
	}

	/** @return si el módulo que genera ese tipo de alerta está apagado; las técnicas no tienen módulo */
	private static boolean moduloApagado(AlertaRecibida alerta, Modulos modulos) {
		return switch (alerta.tipo()) {
			case CAIDA -> !modulos.caidas();
			case FC -> !modulos.fc();
			case SPO2 -> !modulos.spo2();
			case FC_SPO2 -> !modulos.fc() || !modulos.spo2();
			case TENDENCIA -> !modulos.tendencia();
			case INACTIVIDAD -> !modulos.inactividad();
			case ZONA_SEGURA -> !modulos.zonaSegura();
			case BOTON_AYUDA -> !modulos.botonAyuda();
			default -> false;
		};
	}

	/** @return la gravedad configurada para ese tipo, o null si no tiene una fija */
	private Severidad severidadEsperada(AlertaRecibida alerta, PerfilClinico perfil) {
		return switch (alerta.tipo()) {
			case TENDENCIA -> severidadTendencia;
			case ZONA_SEGURA -> perfil.zonaSegura() == null ? null : perfil.zonaSegura().zona().severidad();
			default -> null;
		};
	}

	/**
	 * @return el detalle si ninguna lectura de la evidencia justifica la alerta, o null si alguna la
	 *         justifica, si no hay lecturas o si el tipo no se evalúa con lecturas
	 */
	private static String lecturasEnRango(AlertaRecibida alerta, PerfilClinico perfil) {
		Umbral fc = perfil.fc().umbral();
		Umbral spo2 = perfil.spo2().umbral();
		return switch (alerta.tipo()) {
			case FC -> sinNinguna(alerta, TipoLectura.FC, lectura -> grave(lectura, fc),
					"ninguna supera los límites graves " + plano(fc.graveBajo()) + " y " + plano(fc.graveAlto()));
			case SPO2 -> sinNinguna(alerta, TipoLectura.SPO2, lectura -> grave(lectura, spo2),
					"ninguna está bajo el límite grave " + plano(spo2.graveBajo()));
			case TENDENCIA, FC_SPO2 -> sinNinguna(alerta, null,
					lectura -> !normal(lectura, lectura.tipo() == TipoLectura.FC ? fc : spo2),
					"todas están dentro del rango normal (FC " + plano(fc.normalMin()) + "–" + plano(fc.normalMax())
							+ ", SpO2 desde " + plano(spo2.normalMin()) + ")");
			default -> null;
		};
	}

	/**
	 * @param tipo   la variable que se mira, o null para mirar FC y SpO2
	 * @param motivo cómo se explica que ninguna justifique la alerta
	 * @return el detalle si hay lecturas y ninguna cumple la condición; si no, null
	 */
	private static String sinNinguna(AlertaRecibida alerta, TipoLectura tipo, Predicate<LecturaEvidencia> justifica,
			String motivo) {
		List<LecturaEvidencia> lecturas = alerta.lecturas().stream()
				.filter(lectura -> tipo == null || lectura.tipo() == tipo)
				.toList();
		if (lecturas.isEmpty() || lecturas.stream().anyMatch(justifica)) {
			return null;
		}
		return "Lecturas " + lecturas.stream().map(lectura -> lectura.tipo() + " " + plano(lectura.valor()))
				.collect(Collectors.joining(", ")) + ": " + motivo;
	}

	/** @return si la lectura está más allá de un límite grave (bajo graveBajo o sobre graveAlto) */
	private static boolean grave(LecturaEvidencia lectura, Umbral umbral) {
		BigDecimal valor = BigDecimal.valueOf(lectura.valor());
		return valor.compareTo(umbral.graveBajo()) < 0
				|| (umbral.graveAlto() != null && valor.compareTo(umbral.graveAlto()) > 0);
	}

	/** @return si la lectura está en el rango normal; normalMin y normalMax cuentan como normales */
	private static boolean normal(LecturaEvidencia lectura, Umbral umbral) {
		BigDecimal valor = BigDecimal.valueOf(lectura.valor());
		return valor.compareTo(umbral.normalMin()) >= 0
				&& (umbral.normalMax() == null || valor.compareTo(umbral.normalMax()) <= 0);
	}

	/** @return el número sin ceros sobrantes (95 y no 95.0) */
	private static String plano(double valor) {
		return plano(BigDecimal.valueOf(valor));
	}

	private static String plano(BigDecimal valor) {
		return valor == null ? "" : valor.stripTrailingZeros().toPlainString();
	}

}
