package cl.ucn.alertacuida.adapters.fcm;

import cl.ucn.alertacuida.application.avisoreloj.modelo.AvisoReloj;
import cl.ucn.alertacuida.application.avisoreloj.puerto.CanalAvisoReloj;

/** Canal simulado para desarrollo: no envía nada, solo registra en el log que el aviso habría salido. */
public class CanalAvisoRelojSimulado implements CanalAvisoReloj {

	private static final System.Logger LOG = System.getLogger(CanalAvisoRelojSimulado.class.getName());

	/** El token no se registra: permite enviarle mensajes a ese reloj. */
	@Override
	public void enviar(AvisoReloj aviso) {
		LOG.log(System.Logger.Level.INFO, "Aviso al reloj simulado (no se envió): " + aviso.tipo() + " "
				+ aviso.idAviso());
	}

}
