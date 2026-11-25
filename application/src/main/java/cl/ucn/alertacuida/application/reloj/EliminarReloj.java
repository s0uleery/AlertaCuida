package cl.ucn.alertacuida.application.reloj;

import cl.ucn.alertacuida.application.asignacion.puerto.Asignaciones;
import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.reloj.puerto.BajaRelojes;
import cl.ucn.alertacuida.application.reloj.puerto.Relojes;
import java.time.Clock;
import java.util.UUID;

/**
 * Borra un reloj del inventario, por ejemplo uno registrado por error. Solo si nunca tuvo paciente: uno que lo
 * tuvo guarda lecturas y alertas, y se saca de servicio pasándolo a RETIRADO (decisión del 24-11-2026).
 */
public class EliminarReloj {

	/** Código del conflicto de un reloj vinculado ahora. */
	public static final String EN_USO = "EN_USO";
	/** Código del conflicto de un reloj que tuvo paciente: el dashboard ofrece pasarlo a RETIRADO. */
	public static final String CON_HISTORIAL = "CON_HISTORIAL";

	private final Relojes relojes;
	private final Asignaciones asignaciones;
	private final BajaRelojes baja;
	private final RegistroAuditoria auditoria;
	private final Transacciones transacciones;
	private final Clock reloj;

	/** Recibe los puertos que usa; los arma ConfiguracionPacientesYRelojes. */
	public EliminarReloj(Relojes relojes, Asignaciones asignaciones, BajaRelojes baja, RegistroAuditoria auditoria,
			Transacciones transacciones, Clock reloj) {
		this.relojes = relojes;
		this.asignaciones = asignaciones;
		this.baja = baja;
		this.auditoria = auditoria;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/**
	 * @param id        reloj a borrar
	 * @param idUsuario quien lo borra, para la auditoría
	 * @param ip        desde dónde llegó la petición, para la auditoría
	 * @throws NoEncontrado si el reloj no existe
	 * @throws Conflicto    {@link #EN_USO} si alguien lo usa ahora, o {@link #CON_HISTORIAL} si lo usó antes
	 */
	public void eliminar(UUID id, UUID idUsuario, String ip) {
		relojes.buscar(id).orElseThrow(() -> new NoEncontrado("No existe ese reloj"));
		if (asignaciones.vigenteDelReloj(id).isPresent()) {
			throw new Conflicto("El reloj está vinculado a un usuario: desvincúlalo antes de eliminarlo", EN_USO);
		}
		transacciones.ejecutar(() -> {
			if (baja.tuvoAsignaciones(id) || !baja.eliminarSinHistorial(id)) {
				throw new Conflicto("Este reloj ya tuvo un usuario y guarda sus lecturas y alertas, así que no se "
						+ "puede borrar. Puedes pasarlo a Retirado para sacarlo de servicio.", CON_HISTORIAL);
			}
			auditoria.registrar(idUsuario, "ELIMINAR", "SMARTWATCH", id.toString(), ip, reloj.instant());
			return null;
		});
	}

}
