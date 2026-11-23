package cl.ucn.alertacuida.application.asignacion.puerto;

/** Sorteo de los códigos de emparejamiento que el reloj muestra en pantalla. */
public interface CodigosCortos {

	/**
	 * @return un código nuevo de 8 caracteres, en mayúsculas y sin guion, sin letras que se confundan
	 *         (ni 0/O, ni 1/I/L)
	 */
	String nuevo();

}
