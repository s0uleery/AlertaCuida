package cl.ucn.alertacuida.application.alerta;

import cl.ucn.alertacuida.application.alerta.modelo.AlertaAtendible;
import cl.ucn.alertacuida.application.alerta.modelo.DestinoDerivacion;
import cl.ucn.alertacuida.application.alerta.modelo.TipoSeguimiento;
import cl.ucn.alertacuida.application.alerta.puerto.Seguimientos;
import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Registra lo que hace el equipo en un caso: una llamada, una visita, una derivación o una nota (decisión del
 * 19-11-2026). Se puede mientras la alerta no esté cerrada del todo: sin asignar, en atención o en seguimiento.
 * La línea de tiempo ya muestra estas acciones.
 */
public class RegistrarSeguimiento {

	private final Function<UUID, Optional<AlertaAtendible>> alertas;
	private final Seguimientos seguimientos;
	private final RegistroAuditoria auditoria;
	private final Transacciones transacciones;
	private final Clock reloj;

	/**
	 * Recibe lo que usa; lo arma ConfiguracionAtencionAlertas.
	 *
	 * @param alertas busca la alerta (AtencionAlertas::buscar)
	 */
	public RegistrarSeguimiento(Function<UUID, Optional<AlertaAtendible>> alertas, Seguimientos seguimientos,
			RegistroAuditoria auditoria, Transacciones transacciones, Clock reloj) {
		this.alertas = alertas;
		this.seguimientos = seguimientos;
		this.auditoria = auditoria;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/**
	 * @param destino código del destino; obligatorio en DERIVACION y vacío en las demás
	 * @param nota    obligatoria salvo en DERIVACION
	 * @return el id de la acción
	 * @throws SolicitudRechazada si falta el tipo, la nota o el destino, o el destino no existe o no está activo
	 * @throws NoEncontrado       si la alerta no existe
	 * @throws Conflicto          si el usuario canceló la alerta o ya está cerrada del todo
	 */
	public UUID registrar(UUID idAlerta, TipoSeguimiento tipo, String destino, String nota, UUID idUsuario,
			String ip) {
		String texto = nota == null || nota.isBlank() ? null : nota.strip();
		validar(tipo, destino, texto);
		AlertaAtendible alerta = alertas.apply(idAlerta).orElseThrow(() -> new NoEncontrado("No existe esa alerta"));
		if (alerta.cancelada()) {
			throw new Conflicto("El paciente canceló esa alerta");
		}
		Instant ahora = reloj.instant();
		return transacciones.ejecutar(() -> {
			UUID id = seguimientos.registrar(idAlerta, idUsuario, tipo, destino, texto, ahora)
					.orElseThrow(() -> new Conflicto("Esa alerta ya está cerrada"));
			auditoria.registrar(idUsuario, "CREAR", "SEGUIMIENTO", id.toString(), ip, ahora);
			return id;
		});
	}

	/** @return los destinos a los que se puede derivar hoy */
	public List<DestinoDerivacion> destinos() {
		return seguimientos.destinosActivos();
	}

	/** @throws SolicitudRechazada si la combinación de tipo, destino y nota no sirve */
	private void validar(TipoSeguimiento tipo, String destino, String texto) {
		if (tipo == null) {
			throw new SolicitudRechazada("Falta el tipo de acción");
		}
		if (tipo == TipoSeguimiento.DERIVACION) {
			if (destino == null || !seguimientos.destinoActivo(destino)) {
				throw new SolicitudRechazada("Falta el destino de la derivación o no está disponible");
			}
		} else {
			if (destino != null) {
				throw new SolicitudRechazada("Solo una derivación lleva destino");
			}
			if (texto == null) {
				throw new SolicitudRechazada("Falta la nota");
			}
		}
	}

}
