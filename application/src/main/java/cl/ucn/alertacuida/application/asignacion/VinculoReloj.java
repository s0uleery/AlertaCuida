package cl.ucn.alertacuida.application.asignacion;

import cl.ucn.alertacuida.application.asignacion.modelo.AsignacionVigente;
import cl.ucn.alertacuida.application.asignacion.modelo.TokenReloj;
import cl.ucn.alertacuida.application.asignacion.puerto.Asignaciones;
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
 * Vínculo entre un reloj y un paciente (1.2.3, 1.2.4 y 1.2.5): vincular, reemplazar, desvincular y
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
 * <p>Cada vínculo tiene su token (rol DEVICE): se entrega una sola vez y en la base va su hash.
 * Cerrar el vínculo revoca el token.
 */
public class VinculoReloj {

	private final Asignaciones asignaciones;
	private final Pacientes pacientes;
	private final Relojes relojes;
	private final SecretosUnSoloUso secretos;
	private final RegistroAuditoria auditoria;
	private final Transacciones transacciones;
	private final Clock reloj;

	public VinculoReloj(Asignaciones asignaciones, Pacientes pacientes, Relojes relojes, SecretosUnSoloUso secretos,
			RegistroAuditoria auditoria, Transacciones transacciones, Clock reloj) {
		this.asignaciones = asignaciones;
		this.pacientes = pacientes;
		this.relojes = relojes;
		this.secretos = secretos;
		this.auditoria = auditoria;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/**
	 * Entrega un reloj a un paciente que no tiene.
	 *
	 * @param idPaciente    paciente que recibe el reloj
	 * @param idDispositivo reloj entregado
	 * @param idUsuario     quien lo vincula, para la auditoría
	 * @param ip            desde dónde llegó la petición, para la auditoría
	 * @return el token para configurar el reloj (se muestra una sola vez)
	 * @throws NoEncontrado       si el paciente o el reloj no existen
	 * @throws SolicitudRechazada si el paciente no firmó el consentimiento
	 * @throws Conflicto          si el paciente ya tiene reloj, está dado de baja, o el reloj no está
	 *                            operativo o lo usa otro paciente
	 */
	public TokenReloj vincular(UUID idPaciente, UUID idDispositivo, UUID idUsuario, String ip) {
		pacienteVinculable(idPaciente);
		if (asignaciones.vigenteDelPaciente(idPaciente).isPresent()) {
			throw new Conflicto("El usuario ya tiene un reloj: usa reemplazar");
		}
		relojVinculable(idDispositivo);
		Instant ahora = reloj.instant();
		return transacciones.ejecutar(() -> abrir(idDispositivo, idPaciente, ahora, idUsuario, ip));
	}

	/**
	 * Cambia el reloj de un paciente por otro, en el mismo instante: el anterior termina cuando
	 * empieza el nuevo, así el paciente no queda ni un segundo sin vínculo.
	 *
	 * @param idPaciente      paciente
	 * @param idDispositivo   reloj nuevo
	 * @param estadoAnterior  en qué estado queda el reloj anterior (ej. EN_REPARACION); vacío = sin cambio
	 * @param idUsuario       quien lo reemplaza, para la auditoría
	 * @param ip              desde dónde llegó la petición, para la auditoría
	 * @return el token del reloj nuevo; el del anterior deja de servir
	 * @throws NoEncontrado       si el paciente o el reloj no existen
	 * @throws SolicitudRechazada si el paciente no firmó el consentimiento o el reloj es el mismo
	 * @throws Conflicto          si el paciente no tiene reloj, está dado de baja, o el reloj nuevo no
	 *                            está operativo o lo usa otro paciente
	 */
	public TokenReloj reemplazar(UUID idPaciente, UUID idDispositivo, EstadoReloj estadoAnterior, UUID idUsuario,
			String ip) {
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
			return abrir(idDispositivo, idPaciente, ahora, idUsuario, ip);
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

	private AsignacionVigente vigente(UUID idPaciente) {
		return asignaciones.vigenteDelPaciente(idPaciente)
				.orElseThrow(() -> new Conflicto("El usuario no tiene un reloj vinculado"));
	}

	/** Crea el vínculo con un token nuevo. Llamar dentro de una transacción. */
	private TokenReloj abrir(UUID idDispositivo, UUID idPaciente, Instant ahora, UUID idUsuario, String ip) {
		String token = secretos.nuevo();
		UUID idAsignacion = asignaciones.abrir(idDispositivo, idPaciente, ahora, secretos.hash(token));
		auditoria.registrar(idUsuario, "CREAR", "ASIGNACION_RELOJ", idAsignacion.toString(), ip, ahora);
		return new TokenReloj(idAsignacion, token);
	}

	/** Cierra el vínculo (revoca su token). Llamar dentro de una transacción. */
	private void cerrar(AsignacionVigente vigente, Instant ahora, UUID idUsuario, String ip) {
		asignaciones.cerrar(vigente.idAsignacion(), ahora);
		auditoria.registrar(idUsuario, "MODIFICAR", "ASIGNACION_RELOJ", vigente.idAsignacion().toString(), ip, ahora);
	}

}
