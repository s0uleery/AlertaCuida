package cl.ucn.alertacuida.application.reloj;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.reloj.modelo.DatosReloj;
import cl.ucn.alertacuida.application.reloj.puerto.Relojes;
import java.time.Clock;
import java.util.UUID;

/** Registro de un reloj en el inventario, con su ID único. Queda libre hasta vincularlo. */
public class RegistrarReloj {

	private final Relojes relojes;
	private final RegistroAuditoria auditoria;
	private final Transacciones transacciones;
	private final Clock reloj;

	/** Recibe los puertos que usa; los arma ConfiguracionPacientesYRelojes. */
	public RegistrarReloj(Relojes relojes, RegistroAuditoria auditoria, Transacciones transacciones, Clock reloj) {
		this.relojes = relojes;
		this.auditoria = auditoria;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/**
	 * @param datos     del reloj y su SIM
	 * @param idUsuario quien lo registra, para la auditoría
	 * @param ip        desde dónde llegó la petición, para la auditoría
	 * @return el identificador interno del reloj
	 * @throws SolicitudRechazada si falta el identificador
	 * @throws Conflicto          si el identificador, el ICCID o el número de SIM ya son de otro reloj
	 */
	public UUID registrar(DatosReloj datos, UUID idUsuario, String ip) {
		DatosReloj limpios = ValidacionReloj.validar(datos);
		if (relojes.existeIdentificador(limpios.identificadorReloj(), null)) {
			throw new Conflicto("Ya existe un reloj con ese identificador");
		}
		return transacciones.ejecutar(() -> {
			UUID id = relojes.crear(limpios);
			auditoria.registrar(idUsuario, "CREAR", "SMARTWATCH", id.toString(), ip, reloj.instant());
			return id;
		});
	}

}
