package cl.ucn.alertacuida.domain.telemetria;

/**
 * Variable que mide el reloj cada 30 minutos (registro_vital.tipo), con el rango de valores que
 * el sensor puede entregar. Una lectura fuera de él es un error del sensor, no un dato clínico.
 */
public enum TipoLectura {

	/** Frecuencia cardíaca, en latidos por minuto. */
	FC(20, 250),
	/** Saturación de oxígeno, en %. Las lecturas fallidas el reloj no las envía. */
	SPO2(50, 100);

	private final double minimo;
	private final double maximo;

	/** @param minimo y maximo: rango posible del sensor, ambos incluidos */
	TipoLectura(double minimo, double maximo) {
		this.minimo = minimo;
		this.maximo = maximo;
	}

	/** @return si el valor está dentro de lo que el sensor puede medir */
	public boolean admite(double valor) {
		return valor >= minimo && valor <= maximo;
	}

}
