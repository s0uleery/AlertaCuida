package cl.ucn.alertacuida.application.auditoria;

import cl.ucn.alertacuida.application.auditoria.modelo.RegistroAuditado;
import cl.ucn.alertacuida.application.auditoria.puerto.ConsultaAuditoria;
import java.util.List;

/**
 * Consulta del registro de auditoría. Las lecturas son mucho más numerosas que los cambios, así que
 * se piden aparte para que no los tapen.
 */
public class ConsultarAuditoria {

	/** Máximo de registros por consulta, para no cargar de más el servidor ni el navegador. */
	static final int LIMITE_MAXIMO = 1000;

	private final ConsultaAuditoria consulta;

	/** @param consulta de dónde se leen los registros */
	public ConsultarAuditoria(ConsultaAuditoria consulta) {
		this.consulta = consulta;
	}

	/**
	 * @param limite          cuántos registros se piden; se ajusta al rango de 1 a {@value #LIMITE_MAXIMO}
	 * @param incluirLecturas si también se traen las acciones LEER
	 * @return los registros, del más nuevo al más antiguo
	 */
	public List<RegistroAuditado> recientes(int limite, boolean incluirLecturas) {
		return consulta.recientes(Math.clamp(limite, 1, LIMITE_MAXIMO), incluirLecturas);
	}

}
