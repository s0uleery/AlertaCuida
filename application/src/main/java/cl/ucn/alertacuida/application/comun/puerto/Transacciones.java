package cl.ucn.alertacuida.application.comun.puerto;

import java.util.function.Supplier;

/** Ejecuta varias escrituras como una sola: o quedan todas o ninguna. */
public interface Transacciones {

	<T> T ejecutar(Supplier<T> trabajo);

}
