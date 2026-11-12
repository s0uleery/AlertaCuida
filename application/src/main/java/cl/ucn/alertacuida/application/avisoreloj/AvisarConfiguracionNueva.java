package cl.ucn.alertacuida.application.avisoreloj;

import cl.ucn.alertacuida.application.avisoreloj.modelo.AvisoReloj;
import cl.ucn.alertacuida.application.avisoreloj.puerto.BandejaAvisosReloj;
import cl.ucn.alertacuida.application.avisoreloj.puerto.CanalAvisoReloj;
import cl.ucn.alertacuida.application.contacto.puerto.AvisoCambioContactos;
import java.util.UUID;

/**
 * Avisa al reloj vigente del paciente que cambió su lista de contactos, para que no siga escalando con
 * una lista vieja. Es un intento único y no se guarda: si falla o el reloj no tiene token, se entera en
 * su siguiente sincronización.
 */
public class AvisarConfiguracionNueva implements AvisoCambioContactos {

	private static final System.Logger LOG = System.getLogger(AvisarConfiguracionNueva.class.getName());

	private final BandejaAvisosReloj bandeja;
	private final CanalAvisoReloj canal;

	/** @param canal Firebase o canal simulado */
	public AvisarConfiguracionNueva(BandejaAvisosReloj bandeja, CanalAvisoReloj canal) {
		this.bandeja = bandeja;
		this.canal = canal;
	}

	/** Cualquier falla queda en el log: el cambio de contactos ya se guardó y no debe verse afectado. */
	@Override
	public void listaCambiada(UUID idPaciente) {
		try {
			bandeja.tokenDelPaciente(idPaciente)
					.ifPresent(token -> canal.enviar(AvisoReloj.configuracionNueva(token)));
		} catch (RuntimeException falla) {
			LOG.log(System.Logger.Level.WARNING, "No se pudo avisar la configuración nueva", falla);
		}
	}

}
