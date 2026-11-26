package cl.ucn.alertacuida.application.respuesta.puerto;

import cl.ucn.alertacuida.application.respuesta.modelo.ContactoAvisado;
import cl.ucn.alertacuida.application.respuesta.modelo.EstadoOficina;
import cl.ucn.alertacuida.application.respuesta.modelo.ResultadoLlamadaOficina;
import cl.ucn.alertacuida.application.respuesta.modelo.ResultadoVisita;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Los pasos de la oficina PRLAC en una alerta (columnas nuevas de atencion_alerta, V19). Cada escritura es
 * condicional en la base: solo la hace quien tiene la alerta a cargo, en la etapa que corresponde y con la alerta
 * abierta. Así, de dos pasos a la vez, solo uno lo logra. Deben llamarse dentro de una transacción.
 */
public interface PasosOficina {

	/** @return lo registrado de esa alerta; {@link EstadoOficina#VACIO} si la oficina no la ha tocado */
	EstadoOficina buscar(UUID idAlerta);

	/** @return si la guardó: el responsable aún no registraba la llamada */
	boolean registrarLlamada(UUID idAlerta, UUID idUsuario, ResultadoLlamadaOficina resultado, Instant fecha);

	/** @return si la guardó: la persona necesita atención o no contestó, y aún no se coordinaba la visita */
	boolean asignarPersonal(UUID idAlerta, UUID idUsuario, Instant fecha);

	/** @return si la guardó, con las mismas condiciones de {@link #asignarPersonal} */
	boolean sinDisponibilidad(UUID idAlerta, UUID idUsuario, Instant fecha);

	/** @return si la guardó: había personal asignado y aún no llegaba */
	boolean registrarLlegada(UUID idAlerta, UUID idUsuario, Instant fecha);

	/** @return si la guardó: el personal llegó y aún no se decía cómo la encontró */
	boolean registrarVisita(UUID idAlerta, UUID idUsuario, ResultadoVisita resultado, Instant fecha);

	/**
	 * Agrega el texto a las observaciones del cierre, en un párrafo aparte.
	 *
	 * @return si lo agregó: la alerta está cerrada y la tiene a cargo esa cuenta
	 */
	boolean agregarComentario(UUID idAlerta, UUID idUsuario, String texto);

	/** @return el último contacto del paciente al que se le envió un aviso de esa alerta; vacío si ninguno */
	Optional<ContactoAvisado> ultimoContactoAvisado(UUID idAlerta);

}
