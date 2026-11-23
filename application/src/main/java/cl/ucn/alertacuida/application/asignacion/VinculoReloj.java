package cl.ucn.alertacuida.application.asignacion;

import cl.ucn.alertacuida.application.asignacion.modelo.AsignacionVigente;
import cl.ucn.alertacuida.application.asignacion.modelo.CodigoEmparejamiento;
import cl.ucn.alertacuida.application.asignacion.modelo.TokenReloj;
import cl.ucn.alertacuida.application.asignacion.puerto.Asignaciones;
import cl.ucn.alertacuida.application.asignacion.puerto.Emparejamientos;
import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.paciente.modelo.Paciente;
import cl.ucn.alertacuida.application.paciente.puerto.Pacientes;
import cl.ucn.alertacuida.application.reloj.modelo.Reloj;
import cl.ucn.alertacuida.application.reloj.puerto.Relojes;
import cl.ucn.alertacuida.domain.reloj.EstadoReloj;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Vínculo entre un reloj y un paciente: vincular, reemplazar, desvincular y
 * generar un token nuevo. El historial nunca se pierde: los vínculos se cierran, no se borran.
 *
 * <p>Reglas para vincular (nota de asignacion_reloj en V4):
 * <ul>
 * <li>el paciente sigue en el programa y firmó el consentimiento;</li>
 * <li>el reloj está OPERATIVO;</li>
 * <li>un reloj con un solo paciente a la vez, y un paciente con un solo reloj a la vez.</li>
 * </ul>
 * La base también impide los cruces (EXCLUDE), por si dos personas vinculan al mismo tiempo.
 *
 * <p>Cada vínculo tiene su token (rol DEVICE) y en la base va su hash. Cerrar el vínculo revoca el token.
 * Hay dos formas de que el reloj lo reciba:
 * <ul>
 * <li><b>Con código de emparejamiento</b> (lo normal): el reloj ya tiene su token desde que pidió el
 * código ({@link EmparejarReloj}); al vincular se escribe ese código y el token empieza a servir.
 * La respuesta no trae token.</li>
 * <li><b>Sin código</b> (desarrollo y simulador): se genera un token y se entrega una sola vez, para
 * cargarlo a mano en el reloj.</li>
 * </ul>
 */
public class VinculoReloj {

	private final Asignaciones asignaciones;
	private final Pacientes pacientes;
	private final Relojes relojes;
	private final Emparejamientos emparejamientos;
	private final SecretosUnSoloUso secretos;
	private final RegistroAuditoria auditoria;
	private final Transacciones transacciones;
	private final Clock reloj;

	/** Recibe los puertos que usa; los arma ConfiguracionPacientesYRelojes. */
	public VinculoReloj(Asignaciones asignaciones, Pacientes pacientes, Relojes relojes,
			Emparejamientos emparejamientos, SecretosUnSoloUso secretos, RegistroAuditoria auditoria,
			Transacciones transacciones, Clock reloj) {
		this.asignaciones = asignaciones;
		this.pacientes = pacientes;
		this.relojes = relojes;
		this.emparejamientos = emparejamientos;
		this.secretos = secretos;
		this.auditoria = auditoria;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/** Vincula sin código de emparejamiento: entrega un token nuevo (desarrollo y simulador). */
	public TokenReloj vincular(UUID idPaciente, UUID idDispositivo, UUID idUsuario, String ip) {
		return vincular(idPaciente, idDispositivo, null, idUsuario, ip);
	}

	/**
	 * Entrega un reloj a un paciente que no tiene.
	 *
	 * @param idPaciente    paciente que recibe el reloj
	 * @param idDispositivo reloj entregado
	 * @param codigo        el que muestra el reloj; vacío = sin código, se entrega un token nuevo
	 * @param idUsuario     quien lo vincula, para la auditoría
	 * @param ip            desde dónde llegó la petición, para la auditoría
	 * @return el vínculo; con el token solo si se vinculó sin código (se muestra una sola vez)
	 * @throws NoEncontrado       si el paciente o el reloj no existen
	 * @throws SolicitudRechazada si el paciente no firmó el consentimiento, o el código no existe o venció
	 * @throws Conflicto          si el paciente ya tiene reloj, está dado de baja, o el reloj no está
	 *                            operativo o lo usa otro paciente
	 */
	public TokenReloj vincular(UUID idPaciente, UUID idDispositivo, String codigo, UUID idUsuario, String ip) {
		pacienteVinculable(idPaciente);
		if (asignaciones.vigenteDelPaciente(idPaciente).isPresent()) {
			throw new Conflicto("El usuario ya tiene un reloj: usa reemplazar");
		}
		relojVinculable(idDispositivo);
		Instant ahora = reloj.instant();
		return transacciones.ejecutar(() -> abrir(idDispositivo, idPaciente, codigo, ahora, idUsuario, ip));
	}

	/** Reemplaza sin código de emparejamiento: entrega un token nuevo (desarrollo y simulador). */
	public TokenReloj reemplazar(UUID idPaciente, UUID idDispositivo, EstadoReloj estadoAnterior, UUID idUsuario,
			String ip) {
		return reemplazar(idPaciente, idDispositivo, estadoAnterior, null, idUsuario, ip);
	}

	/**
	 * Cambia el reloj de un paciente por otro, en el mismo instante: el anterior termina cuando
	 * empieza el nuevo, así el paciente no queda ni un segundo sin vínculo.
	 *
	 * @param idPaciente      paciente
	 * @param idDispositivo   reloj nuevo
	 * @param estadoAnterior  en qué estado queda el reloj anterior (ej. EN_REPARACION); vacío = sin cambio
	 * @param codigo          el que muestra el reloj nuevo; vacío = sin código, se entrega un token nuevo
	 * @param idUsuario       quien lo reemplaza, para la auditoría
	 * @param ip              desde dónde llegó la petición, para la auditoría
	 * @return el vínculo nuevo (con su token solo si fue sin código); el token del anterior deja de servir
	 * @throws NoEncontrado       si el paciente o el reloj no existen
	 * @throws SolicitudRechazada si el paciente no firmó el consentimiento, el reloj es el mismo, o el
	 *                            código no existe o venció
	 * @throws Conflicto          si el paciente no tiene reloj, está dado de baja, o el reloj nuevo no
	 *                            está operativo o lo usa otro paciente
	 */
	public TokenReloj reemplazar(UUID idPaciente, UUID idDispositivo, EstadoReloj estadoAnterior, String codigo,
			UUID idUsuario, String ip) {
		pacienteVinculable(idPaciente);
		AsignacionVigente actual = vigente(idPaciente);
		if (actual.idDispositivo().equals(idDispositivo)) {
			throw new SolicitudRechazada("El reloj nuevo es el mismo que ya tiene");
		}
		relojVinculable(idDispositivo);
		Instant ahora = reloj.instant();
		return transacciones.ejecutar(() -> {
			cerrar(actual, ahora, idUsuario, ip);
			if (estadoAnterior != null) {
				relojes.cambiarEstado(actual.idDispositivo(), estadoAnterior);
				auditoria.registrar(idUsuario, "MODIFICAR", "SMARTWATCH", actual.idDispositivo().toString(), ip,
						ahora);
			}
			return abrir(idDispositivo, idPaciente, codigo, ahora, idUsuario, ip);
		});
	}

	/**
	 * Retira el reloj al paciente (por ejemplo, se fue de vacaciones o lo devolvió). Su token deja de
	 * servir. Se permite aunque el paciente esté dado de baja, por si quedó algún vínculo abierto.
	 *
	 * @throws NoEncontrado si el paciente no existe
	 * @throws Conflicto    si no tiene reloj
	 */
	public void desvincular(UUID idPaciente, UUID idUsuario, String ip) {
		pacientes.buscar(idPaciente).orElseThrow(() -> new NoEncontrado("No existe ese usuario"));
		AsignacionVigente actual = vigente(idPaciente);
		Instant ahora = reloj.instant();
		transacciones.ejecutar(() -> {
			cerrar(actual, ahora, idUsuario, ip);
			return null;
		});
	}

	/**
	 * Emite otro token para el reloj vigente del paciente, si el anterior se perdió o se filtró.
	 *
	 * @return el token nuevo; el anterior deja de servir
	 * @throws NoEncontrado si el paciente no existe
	 * @throws Conflicto    si no tiene reloj
	 */
	public TokenReloj nuevoToken(UUID idPaciente, UUID idUsuario, String ip) {
		pacientes.buscar(idPaciente).orElseThrow(() -> new NoEncontrado("No existe ese usuario"));
		AsignacionVigente actual = vigente(idPaciente);
		String token = secretos.nuevo();
		return transacciones.ejecutar(() -> {
			asignaciones.cambiarToken(actual.idAsignacion(), secretos.hash(token));
			auditoria.registrar(idUsuario, "MODIFICAR", "ASIGNACION_RELOJ", actual.idAsignacion().toString(), ip,
					reloj.instant());
			return new TokenReloj(actual.idAsignacion(), token);
		});
	}

	/**
	 * Empareja de nuevo el reloj vigente del paciente, por ejemplo después de restaurarlo de fábrica:
	 * el reloj pide otro código y su nuevo token reemplaza al anterior, sin cerrar el vínculo.
	 *
	 * @param codigo el que muestra el reloj
	 * @return el vínculo, sin token (el reloj ya lo tiene)
	 * @throws NoEncontrado       si el paciente no existe
	 * @throws SolicitudRechazada si el código no existe o venció
	 * @throws Conflicto          si no tiene reloj
	 */
	public TokenReloj emparejar(UUID idPaciente, String codigo, UUID idUsuario, String ip) {
		pacientes.buscar(idPaciente).orElseThrow(() -> new NoEncontrado("No existe ese usuario"));
		AsignacionVigente actual = vigente(idPaciente);
		Instant ahora = reloj.instant();
		return transacciones.ejecutar(() -> {
			asignaciones.cambiarToken(actual.idAsignacion(), usarCodigo(codigo, ahora));
			auditoria.registrar(idUsuario, "MODIFICAR", "ASIGNACION_RELOJ", actual.idAsignacion().toString(), ip,
					ahora);
			return new TokenReloj(actual.idAsignacion(), null);
		});
	}

	/** Revisa que el paciente exista, siga en el programa y haya firmado el consentimiento. */
	private void pacienteVinculable(UUID idPaciente) {
		Paciente paciente = pacientes.buscar(idPaciente).orElseThrow(() -> new NoEncontrado("No existe ese usuario"));
		if (!paciente.activo()) {
			throw new Conflicto("El usuario está dado de baja");
		}
		if (paciente.datos().fechaConsentimiento() == null) {
			throw new SolicitudRechazada("El usuario no tiene registrado el consentimiento informado");
		}
	}

	/** Revisa que el reloj exista, esté operativo y no lo use otro paciente. */
	private void relojVinculable(UUID idDispositivo) {
		Reloj encontrado = relojes.buscar(idDispositivo).orElseThrow(() -> new NoEncontrado("No existe ese reloj"));
		if (!encontrado.datos().estado().vinculable()) {
			throw new Conflicto("El reloj no está operativo");
		}
		if (asignaciones.vigenteDelReloj(idDispositivo).isPresent()) {
			throw new Conflicto("El reloj ya está vinculado a otro usuario");
		}
	}

	/**
	 * @return el vínculo vigente del paciente
	 * @throws Conflicto si no tiene reloj
	 */
	private AsignacionVigente vigente(UUID idPaciente) {
		return asignaciones.vigenteDelPaciente(idPaciente)
				.orElseThrow(() -> new Conflicto("El usuario no tiene un reloj vinculado"));
	}

	/**
	 * Crea el vínculo. Con código, usa el token que ya tiene el reloj; sin código, genera uno nuevo.
	 * Llamar dentro de una transacción: si algo falla, el código vuelve a quedar pendiente.
	 */
	private TokenReloj abrir(UUID idDispositivo, UUID idPaciente, String codigo, Instant ahora, UUID idUsuario,
			String ip) {
		boolean conCodigo = codigo != null && !codigo.isBlank();
		String token = conCodigo ? null : secretos.nuevo();
		String hash = conCodigo ? usarCodigo(codigo, ahora) : secretos.hash(token);
		UUID idAsignacion = asignaciones.abrir(idDispositivo, idPaciente, ahora, hash);
		auditoria.registrar(idUsuario, "CREAR", "ASIGNACION_RELOJ", idAsignacion.toString(), ip, ahora);
		return new TokenReloj(idAsignacion, token);
	}

	/**
	 * Usa el código de emparejamiento (sirve una sola vez). Llamar dentro de una transacción.
	 *
	 * @return el hash del token que ya tiene el reloj
	 * @throws SolicitudRechazada si el código no existe o venció
	 */
	private String usarCodigo(String codigo, Instant ahora) {
		String normalizado = CodigoEmparejamiento.normalizar(codigo);
		if (normalizado.isEmpty()) {
			throw new SolicitudRechazada("Escribe el código que muestra el reloj");
		}
		return emparejamientos.usar(secretos.hash(normalizado), ahora)
				.orElseThrow(() -> new SolicitudRechazada(
						"El código no existe o venció: pide uno nuevo en el reloj y vuelve a escribirlo"));
	}

	/** Cierra el vínculo (revoca su token). Llamar dentro de una transacción. */
	private void cerrar(AsignacionVigente vigente, Instant ahora, UUID idUsuario, String ip) {
		asignaciones.cerrar(vigente.idAsignacion(), ahora);
		auditoria.registrar(idUsuario, "MODIFICAR", "ASIGNACION_RELOJ", vigente.idAsignacion().toString(), ip, ahora);
	}

}
