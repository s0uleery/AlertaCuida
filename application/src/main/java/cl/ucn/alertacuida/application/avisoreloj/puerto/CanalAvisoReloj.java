package cl.ucn.alertacuida.application.avisoreloj.puerto;

import cl.ucn.alertacuida.application.avisoreloj.error.TokenRelojRechazado;
import cl.ucn.alertacuida.application.avisoreloj.modelo.AvisoReloj;

/** Por dónde salen los avisos hacia el reloj: Firebase o un canal simulado. */
public interface CanalAvisoReloj {

	/**
	 * @throws TokenRelojRechazado si el token ya no sirve
	 * @throws RuntimeException    si el envío falló por otra causa y conviene volver a intentarlo
	 */
	void enviar(AvisoReloj aviso);

}
