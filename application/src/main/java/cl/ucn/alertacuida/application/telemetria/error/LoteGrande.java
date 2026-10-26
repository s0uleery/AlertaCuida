package cl.ucn.alertacuida.application.telemetria.error;

/** El lote trae más elementos de los permitidos (responde 413): el reloj debe partirlo. */
public class LoteGrande extends RuntimeException {

	/** @param mensaje por ejemplo, "Más de 2000 lecturas en un lote" */
	public LoteGrande(String mensaje) {
		super(mensaje);
	}

}
