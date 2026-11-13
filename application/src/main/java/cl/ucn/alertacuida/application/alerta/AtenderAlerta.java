package cl.ucn.alertacuida.application.alerta;

import cl.ucn.alertacuida.application.alerta.modelo.AlertaAtendible;
import cl.ucn.alertacuida.application.alerta.puerto.AtencionAlertas;
import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.domain.alerta.ClasificacionAlerta;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Lo que hace el equipo PRLAC con una alerta: tomar el caso, resolverlo y cerrar su seguimiento. Una
 * alerta que el paciente canceló no tiene atención.
 */
public class AtenderAlerta {

	private static final String ENTIDAD = "ATENCION_ALERTA";
	private static final String MODIFICAR = "MODIFICAR";

	private final AtencionAlertas atencion;
	private final RegistroAuditoria auditoria;
	private final Transacciones transacciones;
	private final Clock reloj;

	/** Recibe lo que usa; lo arma ConfiguracionAtencionAlertas. */
	public AtenderAlerta(AtencionAlertas atencion, RegistroAuditoria auditoria, Transacciones transacciones,
			Clock reloj) {
		this.atencion = atencion;
		this.auditoria = auditoria;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/**
	 * La persona queda a cargo del caso.
	 *
	 * @throws NoEncontrado si la alerta no existe
	 * @throws Conflicto    si la canceló el paciente, otra persona ya la tomó o ya está cerrada
	 */
	public void asumir(UUID idAlerta, UUID idUsuario, String ip) {
		alertaNoCancelada(idAlerta);
		Instant ahora = reloj.instant();
		transacciones.ejecutar(() -> {
			if (!atencion.asumir(idAlerta, idUsuario, ahora)) {
				throw new Conflicto("Esa alerta ya la tomó otra persona o ya está cerrada");
			}
			auditoria.registrar(idUsuario, MODIFICAR, ENTIDAD, idAlerta.toString(), ip, ahora);
			return null;
		});
	}

	/**
	 * Cierra la alerta. Puede hacerlo cualquier persona con permiso, aunque otra la tenga a cargo; si
	 * nadie la había asumido, queda como responsable. Si la envió el reloj, deja pendiente el aviso para
	 * que detenga su escalamiento.
	 *
	 * @param clasificacion obligatoria en las alertas de salud; las técnicas no se clasifican
	 * @param observaciones obligatorias en toda alerta
	 * @throws NoEncontrado       si la alerta no existe
	 * @throws Conflicto          si la canceló el paciente o ya está cerrada
	 * @throws SolicitudRechazada si falta un dato o la clasificación no corresponde
	 */
	public void resolver(UUID idAlerta, ClasificacionAlerta clasificacion, String observaciones, UUID idUsuario,
			String ip) {
		AlertaAtendible alerta = alertaNoCancelada(idAlerta);
		validarResolucion(alerta, clasificacion, observaciones);
		Instant ahora = reloj.instant();
		transacciones.ejecutar(() -> {
			if (!atencion.resolver(idAlerta, idUsuario, clasificacion, observaciones.strip(), ahora)) {
				throw new Conflicto("Esa alerta ya está cerrada");
			}
			if (alerta.delReloj()) {
				atencion.dejarAvisoResueltaPendiente(idAlerta);
			}
			auditoria.registrar(idUsuario, MODIFICAR, ENTIDAD, idAlerta.toString(), ip, ahora);
			return null;
		});
	}

	/**
	 * Termina el seguimiento de una alerta POSITIVA.
	 *
	 * @throws NoEncontrado si la alerta no existe
	 * @throws Conflicto    si la alerta no tiene un seguimiento abierto
	 */
	public void cerrarSeguimiento(UUID idAlerta, UUID idUsuario, String ip) {
		buscar(idAlerta);
		Instant ahora = reloj.instant();
		transacciones.ejecutar(() -> {
			if (!atencion.cerrarSeguimiento(idAlerta, ahora)) {
				throw new Conflicto("Esa alerta no tiene un seguimiento abierto");
			}
			auditoria.registrar(idUsuario, MODIFICAR, ENTIDAD, idAlerta.toString(), ip, ahora);
			return null;
		});
	}

	/** @throws SolicitudRechazada si faltan las observaciones o la clasificación no calza con el tipo */
	private static void validarResolucion(AlertaAtendible alerta, ClasificacionAlerta clasificacion,
			String observaciones) {
		if (observaciones == null || observaciones.isBlank()) {
			throw new SolicitudRechazada("Faltan las observaciones");
		}
		if (alerta.tipo().esTecnica() && clasificacion != null) {
			throw new SolicitudRechazada("Una alerta técnica no se clasifica");
		}
		if (!alerta.tipo().esTecnica() && clasificacion == null) {
			throw new SolicitudRechazada("Falta la clasificación de la alerta");
		}
	}

	/** @throws Conflicto si la canceló el paciente */
	private AlertaAtendible alertaNoCancelada(UUID idAlerta) {
		AlertaAtendible alerta = buscar(idAlerta);
		if (alerta.cancelada()) {
			throw new Conflicto("El paciente canceló esa alerta");
		}
		return alerta;
	}

	/** @throws NoEncontrado si la alerta no existe */
	private AlertaAtendible buscar(UUID idAlerta) {
		return atencion.buscar(idAlerta).orElseThrow(() -> new NoEncontrado("No existe esa alerta"));
	}

}
