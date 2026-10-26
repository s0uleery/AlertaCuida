package cl.ucn.alertacuida.application.comun.puerto;

import cl.ucn.alertacuida.domain.comun.Run;

/** Calcula el rut_hash con el que se busca al usuario sin descifrar su RUN. */
public interface HashRun {

	/**
	 * @param run RUN normalizado
	 * @return el hash; el mismo RUN da siempre el mismo hash, así se puede buscar
	 */
	String de(Run run);

}
