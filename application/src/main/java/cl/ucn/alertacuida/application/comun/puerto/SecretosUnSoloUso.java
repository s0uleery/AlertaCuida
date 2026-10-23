package cl.ucn.alertacuida.application.comun.puerto;

/**
 * Tokens aleatorios para los enlaces de un solo uso, y el hash con que se guardan.
 * La base solo conoce el hash: quien la lea no obtiene enlaces que funcionen.
 */
public interface SecretosUnSoloUso {

	/** @return un token nuevo, imposible de adivinar y apto para ir en una URL */
	String nuevo();

	/**
	 * @param secreto un token emitido por {@link #nuevo()}
	 * @return su hash; el mismo token da siempre el mismo hash, así se puede buscar
	 */
	String hash(String secreto);

}
