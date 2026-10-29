package cl.ucn.alertacuida.application.configuracion.modelo;

import cl.ucn.alertacuida.domain.contacto.Canal;
import cl.ucn.alertacuida.domain.contacto.Horario;
import java.util.List;
import java.util.UUID;

/**
 * Un contacto tal como lo recibe el reloj: sin número ni correo. El reloj pide los avisos por
 * {@code idContacto} y el servidor elige el número o el correo.
 *
 * @param idContacto el vínculo contacto_paciente, el mismo que usa la solicitud de aviso
 * @param nombre     para la pantalla "Alerta recibida por ..."
 * @param prioridad  orden en que el reloj avisa (1 = primero)
 * @param horario    cuándo recibe avisos, en hora de Chile; vacío = todo el día
 * @param canales    los que el servidor puede usar hoy (WhatsApp solo con opt-in vigente)
 */
public record ContactoConfigurado(UUID idContacto, String nombre, int prioridad, Horario horario,
		List<Canal> canales) {

	public ContactoConfigurado {
		canales = List.copyOf(canales);
	}

}
