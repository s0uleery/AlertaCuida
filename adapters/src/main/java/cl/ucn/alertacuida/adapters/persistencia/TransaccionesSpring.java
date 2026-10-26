package cl.ucn.alertacuida.adapters.persistencia;

import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transacciones con Spring, para que los casos de uso no dependan de {@code @Transactional}.
 * Si el trabajo lanza una excepción no chequeada, se deshacen todas sus escrituras.
 */
@Component
public class TransaccionesSpring implements Transacciones {

	private final TransactionTemplate plantilla;

	/** @param plantilla la que Spring Boot crea sobre la conexión a PostgreSQL */
	public TransaccionesSpring(TransactionTemplate plantilla) {
		this.plantilla = plantilla;
	}

	/**
	 * @param trabajo escrituras que deben quedar todas o ninguna
	 * @return lo que devuelve el trabajo
	 */
	@Override
	public <T> T ejecutar(Supplier<T> trabajo) {
		return plantilla.execute(estado -> trabajo.get());
	}

}
