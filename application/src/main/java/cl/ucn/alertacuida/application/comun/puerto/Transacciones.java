package cl.ucn.alertacuida.application.comun.puerto;

import java.util.function.Supplier;

/** Ejecuta varias escrituras como una sola: o quedan todas o ninguna. */
public interface Transacciones {

	/**
	 * @param trabajo escrituras que deben quedar todas o ninguna
	 * @return lo que devuelve el trabajo
	 */
	<T> T ejecutar(Supplier<T> trabajo);

}
