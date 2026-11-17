package cl.ucn.alertacuida.application.avisoreloj.puerto;

import cl.ucn.alertacuida.application.avisoreloj.modelo.AvisoPendiente;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Avisos al reloj sobre sus alertas (tabla aviso_fcm) y token de Firebase de cada reloj. */
public interface BandejaAvisosReloj {

	/**
	 * @param maximoEnvios desde cuántos envíos un aviso ya no se reintenta
	 * @return los avisos sin acusar, de relojes con token, que aún se pueden enviar
	 */
	List<AvisoPendiente> sinAcusar(int maximoEnvios);

	/** Registra un envío: guarda su hora y suma un intento, salvo que el reloj ya lo haya acusado. */
	void marcarEnviado(UUID idAviso, Instant ahora);

	/** Borra el token rechazado, salvo que el reloj ya haya registrado otro. */
	void olvidarToken(UUID idDispositivo, String tokenRechazado);

	/** @return si ese aviso es de una alerta de ese vínculo */
	boolean esDelVinculo(UUID idAviso, UUID idAsignacion);

	/** Registra cuándo lo recibió el reloj. No cambia una hora ya registrada. */
	void registrarRecepcion(UUID idAviso, Instant fecha);

	/** @return el token del reloj vigente del paciente, si tiene */
	Optional<String> tokenDelPaciente(UUID idPaciente);

}
