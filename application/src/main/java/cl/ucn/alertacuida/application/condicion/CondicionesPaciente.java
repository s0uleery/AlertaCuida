package cl.ucn.alertacuida.application.condicion;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.condicion.modelo.Condicion;
import cl.ucn.alertacuida.application.condicion.puerto.Condiciones;
import cl.ucn.alertacuida.application.paciente.puerto.Pacientes;
import cl.ucn.alertacuida.domain.paciente.TipoCondicion;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Enfermedades y situaciones de un paciente: listar, agregar y quitar. Como la edición del
 * paciente, se permite también con pacientes dados de baja, para corregir su historial.
 *
 * <p>Repetidas y orden se revisan aquí y no en la base: la descripción irá cifrada.
 */
public class CondicionesPaciente {

	/** Largo máximo de una descripción. */
	static final int LARGO_MAXIMO = 200;

	private final Condiciones condiciones;
	private final Pacientes pacientes;
	private final RegistroAuditoria auditoria;
	private final Transacciones transacciones;
	private final Clock reloj;

	/** Recibe los puertos que usa; los arma ConfiguracionPacientesYRelojes. */
	public CondicionesPaciente(Condiciones condiciones, Pacientes pacientes, RegistroAuditoria auditoria,
			Transacciones transacciones, Clock reloj) {
		this.condiciones = condiciones;
		this.pacientes = pacientes;
		this.auditoria = auditoria;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/**
	 * @param idPaciente paciente
	 * @return sus condiciones: primero las enfermedades, luego las situaciones, cada grupo por orden alfabético
	 * @throws NoEncontrado si el paciente no existe
	 */
	public List<Condicion> listar(UUID idPaciente) {
		exigirPaciente(idPaciente);
		return condiciones.delPaciente(idPaciente).stream()
				.sorted(Comparator.comparing(Condicion::tipo)
						.thenComparing(Condicion::descripcion, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	/**
	 * @param idPaciente  paciente
	 * @param tipo        enfermedad o situación
	 * @param descripcion texto libre; se guarda sin espacios sobrantes
	 * @param idUsuario   quien la agrega, para la auditoría
	 * @param ip          desde dónde llegó la petición, para la auditoría
	 * @return el identificador de la condición
	 * @throws NoEncontrado       si el paciente no existe
	 * @throws SolicitudRechazada si falta el tipo, o la descripción está vacía o es muy larga
	 * @throws Conflicto          si el paciente ya tiene esa condición (sin importar mayúsculas)
	 */
	public UUID agregar(UUID idPaciente, TipoCondicion tipo, String descripcion, UUID idUsuario, String ip) {
		exigirPaciente(idPaciente);
		String limpia = descripcion == null ? "" : descripcion.strip().replaceAll("\\s+", " ");
		if (tipo == null) {
			throw new SolicitudRechazada("Indica si es una enfermedad o una situación");
		}
		if (limpia.isEmpty()) {
			throw new SolicitudRechazada("La descripción es obligatoria");
		}
		if (limpia.length() > LARGO_MAXIMO) {
			throw new SolicitudRechazada("La descripción no puede tener más de " + LARGO_MAXIMO + " caracteres");
		}
		boolean repetida = condiciones.delPaciente(idPaciente).stream()
				.anyMatch(c -> c.tipo() == tipo && c.descripcion().equalsIgnoreCase(limpia));
		if (repetida) {
			throw new Conflicto("El usuario ya tiene registrada esa condición");
		}
		return transacciones.ejecutar(() -> {
			UUID id = condiciones.crear(idPaciente, tipo, limpia);
			auditoria.registrar(idUsuario, "CREAR", "CONDICION_PACIENTE", id.toString(), ip, reloj.instant());
			return id;
		});
	}

	/**
	 * @param idPaciente  paciente
	 * @param idCondicion condición a quitar
	 * @param idUsuario   quien la quita, para la auditoría
	 * @param ip          desde dónde llegó la petición, para la auditoría
	 * @throws NoEncontrado si el paciente no existe, o la condición no existe o es de otro paciente
	 */
	public void quitar(UUID idPaciente, UUID idCondicion, UUID idUsuario, String ip) {
		exigirPaciente(idPaciente);
		transacciones.ejecutar(() -> {
			if (!condiciones.eliminar(idPaciente, idCondicion)) {
				throw new NoEncontrado("El usuario no tiene esa condición");
			}
			auditoria.registrar(idUsuario, "ELIMINAR", "CONDICION_PACIENTE", idCondicion.toString(), ip,
					reloj.instant());
			return null;
		});
	}

	/** @throws NoEncontrado si el paciente no existe */
	private void exigirPaciente(UUID idPaciente) {
		pacientes.buscar(idPaciente).orElseThrow(() -> new NoEncontrado("No existe ese usuario"));
	}

}
