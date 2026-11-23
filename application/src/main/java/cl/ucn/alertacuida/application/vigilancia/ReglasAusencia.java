package cl.ucn.alertacuida.application.vigilancia;

import cl.ucn.alertacuida.application.vigilancia.modelo.ParametrosVigilancia;
import java.util.List;

/** Arma las reglas de ausencia que aplica el vigilante. */
public final class ReglasAusencia {

	private ReglasAusencia() {
	}

	/**
	 * @param parametros los valores de las reglas
	 * @return todas las reglas
	 */
	public static List<ReglaAusencia> todas(ParametrosVigilancia parametros) {
		return List.of(new ReglaSilencioDatos(parametros.silencio()), new ReglaBateriaBaja(parametros.bateriaMinima()),
				new ReglaRelojNoPuesto(parametros.horaInicioDia(), parametros.horaFinDia()));
	}

}
