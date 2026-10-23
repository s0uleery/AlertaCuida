package cl.ucn.alertacuida.application.reloj;

import cl.ucn.alertacuida.application.asignacion.puerto.Asignaciones;
import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.reloj.modelo.DatosReloj;
import cl.ucn.alertacuida.application.reloj.puerto.Relojes;
import java.time.Clock;
import java.util.UUID;

/** Edición de un reloj del inventario: datos de la SIM, vencimiento del plan y estado físico. */
public class EditarReloj {

	private final Relojes relojes;
	private final Asignaciones asignaciones;
	private final RegistroAuditoria auditoria;
	private final Transacciones transacciones;
	private final Clock reloj;

	public EditarReloj(Relojes relojes, Asignaciones asignaciones, RegistroAuditoria auditoria,
			Transacciones transacciones, Clock reloj) {
		this.relojes = relojes;
		this.asignaciones = asignaciones;
		this.auditoria = auditoria;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/**
	 * @param id        reloj a editar
	 * @param datos     sus datos completos (reemplazan los anteriores)
	 * @param idUsuario quien lo edita, para la auditoría
	 * @param ip        desde dónde llegó la petición, para la auditoría
	 * @throws NoEncontrado       si el reloj no existe
	 * @throws SolicitudRechazada si falta el identificador
	 * @throws Conflicto          si el identificador o la SIM son de otro reloj, o si se quiere sacar de
	 *                            servicio un reloj que alguien está usando
	 */
	public void editar(UUID id, DatosReloj datos, UUID idUsuario, String ip) {
		relojes.buscar(id).orElseThrow(() -> new NoEncontrado("No existe ese reloj"));
		DatosReloj limpios = ValidacionReloj.validar(datos);
		if (relojes.existeIdentificador(limpios.identificadorReloj(), id)) {
			throw new Conflicto("Ya existe otro reloj con ese identificador");
		}
		// Un reloj en uso sigue mandando datos: primero hay que reemplazarlo o desvincularlo
		if (!limpios.estado().vinculable() && asignaciones.vigenteDelReloj(id).isPresent()) {
			throw new Conflicto("El reloj está vinculado a un usuario: reemplázalo o desvincúlalo antes");
		}
		transacciones.ejecutar(() -> {
			relojes.actualizar(id, limpios);
			auditoria.registrar(idUsuario, "MODIFICAR", "SMARTWATCH", id.toString(), ip, reloj.instant());
			return null;
		});
	}

}
