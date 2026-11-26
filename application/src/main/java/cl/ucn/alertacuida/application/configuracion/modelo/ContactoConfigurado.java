package cl.ucn.alertacuida.application.configuracion.modelo;

import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.contacto.Canal;
import cl.ucn.alertacuida.domain.contacto.Horario;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Un contacto tal como lo recibe el reloj: sin número ni correo. El reloj pide los avisos por
 * {@code idContacto} y el servidor elige el número o el correo.
 *
 * @param idContacto el vínculo contacto_paciente, el mismo que usa la solicitud de aviso
 * @param nombre     para la pantalla "Alerta recibida por ..."
 * @param tipo        código de tipo_contacto (TERCERO_SIGNIFICATIVO, CARABINEROS...)
 * @param prioridades en qué gravedades recibe avisos y en qué lugar de cada una (1 = primero): una alerta
 *                    recorre solo los contactos de su gravedad
 * @param horario     cuándo recibe avisos, en hora de Chile; vacío = todo el día
 * @param canales     los que el servidor puede usar hoy (WhatsApp solo con opt-in vigente)
 */
public record ContactoConfigurado(UUID idContacto, String nombre, String tipo, Map<Severidad, Integer> prioridades,
		Horario horario, List<Canal> canales) {

	public ContactoConfigurado {
		Map<Severidad, Integer> copia = new EnumMap<>(Severidad.class);
		copia.putAll(prioridades);
		prioridades = Collections.unmodifiableMap(copia);
		canales = List.copyOf(canales);
	}

}
