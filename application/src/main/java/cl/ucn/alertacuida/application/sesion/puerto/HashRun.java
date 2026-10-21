package cl.ucn.alertacuida.application.sesion.puerto;

import cl.ucn.alertacuida.domain.personal.Run;

/** Calcula el rut_hash con el que se busca al usuario sin descifrar su RUN. */
public interface HashRun {

	String de(Run run);

}
