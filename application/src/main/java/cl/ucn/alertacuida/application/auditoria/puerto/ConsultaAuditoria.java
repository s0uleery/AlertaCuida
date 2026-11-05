package cl.ucn.alertacuida.application.auditoria.puerto;

import cl.ucn.alertacuida.application.auditoria.modelo.RegistroAuditado;
import java.util.List;

/** Lectura del registro de auditoría. */
public interface ConsultaAuditoria {

	/**
	 * @param limite          cuántos registros traer como máximo
	 * @param incluirLecturas si también se traen las acciones LEER
	 * @return los registros, del más nuevo al más antiguo
	 */
	List<RegistroAuditado> recientes(int limite, boolean incluirLecturas);

}
