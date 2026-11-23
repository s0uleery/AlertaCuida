package cl.ucn.alertacuida.application.auditoria;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import java.time.Clock;
import java.util.UUID;

/**
 * Deja constancia de que alguien leyó datos de pacientes. Se llama después de leer con éxito: una
 * consulta fallida no entregó datos. Si no se puede registrar, la petición falla y los datos no salen.
 */
public class RegistrarLectura {

	/** Identificador que se registra cuando se leyó una lista completa y no un objeto. */
	public static final String LISTA = "LISTA";

	private static final String ACCION = "LEER";

	private final RegistroAuditoria auditoria;
	private final Clock reloj;

	/**
	 * @param auditoria dónde queda el registro
	 * @param reloj     hora actual
	 */
	public RegistrarLectura(RegistroAuditoria auditoria, Clock reloj) {
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/**
	 * @param idUsuario quién leyó
	 * @param entidad   qué tipo de dato (valores de registro_auditoria.entidad)
	 * @param idEntidad de qué objeto, o {@link #LISTA}; nunca un dato personal
	 * @param ip        desde dónde llegó la petición
	 */
	public void registrar(UUID idUsuario, String entidad, String idEntidad, String ip) {
		auditoria.registrar(idUsuario, ACCION, entidad, idEntidad, ip, reloj.instant());
	}

}
