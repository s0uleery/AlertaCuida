package cl.ucn.alertacuida.application.asignacion;

import cl.ucn.alertacuida.application.asignacion.error.DemasiadosEmparejamientos;
import cl.ucn.alertacuida.application.asignacion.modelo.CodigoEmparejamiento;
import cl.ucn.alertacuida.application.asignacion.modelo.EstadoEmparejamiento;
import cl.ucn.alertacuida.application.asignacion.puerto.Asignaciones;
import cl.ucn.alertacuida.application.asignacion.puerto.CodigosCortos;
import cl.ucn.alertacuida.application.asignacion.puerto.Emparejamientos;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Lado del reloj del emparejamiento con código (como en los Smart TV):
 * <ol>
 * <li>el reloj, sin token, pide un código: recibe el código corto y el token que usará;</li>
 * <li>muestra el código y pregunta cada pocos segundos si ya lo vincularon;</li>
 * <li>quien instala escribe el código en el dashboard ({@link VinculoReloj}) y el token empieza a servir.</li>
 * </ol>
 * Nadie ve ni escribe el token, y la base solo guarda hashes. El código vence y sirve una sola vez.
 */
public class EmparejarReloj {

	/** Veces que se sortea otro código si sale uno que ya está pendiente (casi imposible). */
	private static final int SORTEOS = 5;

	private final Emparejamientos emparejamientos;
	private final Asignaciones asignaciones;
	private final CodigosCortos codigos;
	private final SecretosUnSoloUso secretos;
	private final Duration vigencia;
	private final int maximoPendientes;
	private final int segundosConsulta;
	private final Clock reloj;

	/**
	 * Recibe los puertos que usa; los arma ConfiguracionApiReloj.
	 *
	 * @param vigencia         cuánto dura un código
	 * @param maximoPendientes sobre este número de códigos pendientes se responde 429
	 * @param segundosConsulta cada cuántos segundos el reloj debe preguntar si ya lo vincularon
	 */
	public EmparejarReloj(Emparejamientos emparejamientos, Asignaciones asignaciones, CodigosCortos codigos,
			SecretosUnSoloUso secretos, Duration vigencia, int maximoPendientes, int segundosConsulta, Clock reloj) {
		this.emparejamientos = emparejamientos;
		this.asignaciones = asignaciones;
		this.codigos = codigos;
		this.secretos = secretos;
		this.vigencia = vigencia;
		this.maximoPendientes = maximoPendientes;
		this.segundosConsulta = segundosConsulta;
		this.reloj = reloj;
	}

	/**
	 * Entrega un código nuevo y el token que tendrá el reloj. De paso borra los códigos vencidos.
	 *
	 * @throws DemasiadosEmparejamientos si ya hay demasiados pendientes
	 */
	public CodigoEmparejamiento iniciar() {
		Instant ahora = reloj.instant();
		emparejamientos.borrarVencidos(ahora);
		if (emparejamientos.pendientes(ahora) >= maximoPendientes) {
			throw new DemasiadosEmparejamientos("Hay demasiados códigos pendientes: intenta en unos minutos");
		}
		String token = secretos.nuevo();
		Instant expira = ahora.plus(vigencia);
		for (int intento = 0; intento < SORTEOS; intento++) {
			String codigo = codigos.nuevo();
			if (emparejamientos.guardar(secretos.hash(codigo), secretos.hash(token), ahora, expira)) {
				return new CodigoEmparejamiento(formatear(codigo), token, expira, segundosConsulta);
			}
		}
		throw new IllegalStateException("No se pudo sortear un código libre");
	}

	/**
	 * @param token el que recibió el reloj al pedir el código
	 * @return VINCULADO si su token ya sirve, PENDIENTE si el código sigue esperando, VENCIDO si no
	 */
	public EstadoEmparejamiento consultar(String token) {
		if (token == null || token.isBlank()) {
			return EstadoEmparejamiento.VENCIDO;
		}
		String hash = secretos.hash(token);
		if (asignaciones.vigentePorHashToken(hash).isPresent()) {
			return EstadoEmparejamiento.VINCULADO;
		}
		return emparejamientos.pendiente(hash, reloj.instant()) ? EstadoEmparejamiento.PENDIENTE
				: EstadoEmparejamiento.VENCIDO;
	}

	/** @return el código con un guion al medio, más fácil de leer en la pantalla del reloj */
	private static String formatear(String codigo) {
		int mitad = codigo.length() / 2;
		return codigo.substring(0, mitad) + "-" + codigo.substring(mitad);
	}

}
