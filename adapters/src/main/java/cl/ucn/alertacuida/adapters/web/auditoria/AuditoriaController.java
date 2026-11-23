package cl.ucn.alertacuida.adapters.web.auditoria;

import cl.ucn.alertacuida.adapters.web.Permisos;
import cl.ucn.alertacuida.application.auditoria.ConsultarAuditoria;
import cl.ucn.alertacuida.application.auditoria.modelo.RegistroAuditado;
import com.fasterxml.jackson.annotation.JsonRawValue;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Registro de auditoría para el administrador. Solo lectura: el registro no se edita ni se borra. */
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

	private final ConsultarAuditoria consultar;

	/** @param consultar caso de uso de la consulta del registro */
	public AuditoriaController(ConsultarAuditoria consultar) {
		this.consultar = consultar;
	}

	/**
	 * Quién hizo la acción.
	 *
	 * @param id     identificador de la cuenta
	 * @param nombre nombre del profesional
	 */
	public record Autor(UUID id, String nombre) {
	}

	/**
	 * Fila de la respuesta. No trae datos personales del objeto, solo su identificador.
	 *
	 * @param id        identificador de la fila
	 * @param fechaHora cuándo ocurrió
	 * @param accion    LEER, CREAR, MODIFICAR, LOGIN, …
	 * @param entidad   tipo de objeto; null en los inicios de sesión
	 * @param idEntidad identificador del objeto; null en los inicios de sesión
	 * @param detalle   JSON guardado en la base, tal cual; null si no hay
	 * @param usuario   quién lo hizo; null en un login fallido con un RUN que no existe
	 * @param ip        desde dónde llegó la petición
	 */
	public record Fila(UUID id, Instant fechaHora, String accion, String entidad, String idEntidad,
			@JsonRawValue String detalle, Autor usuario, String ip) {

		/** @return la fila del caso de uso en el formato JSON del dashboard */
		static Fila de(RegistroAuditado registro) {
			Autor autor = registro.idUsuario() == null ? null
					: new Autor(registro.idUsuario(), registro.nombreUsuario());
			return new Fila(registro.id(), registro.fechaHora(), registro.accion(), registro.entidad(),
					registro.idEntidad(), registro.detalleJson(), autor, registro.ip());
		}

	}

	/**
	 * @param limite          cuántos registros se piden; se ajusta al máximo permitido
	 * @param incluirLecturas si también se traen las acciones LEER (por defecto, no)
	 * @return los registros, del más nuevo al más antiguo
	 */
	@GetMapping
	@PreAuthorize(Permisos.ADMINISTRACION_VER)
	public List<Fila> recientes(@RequestParam(defaultValue = "500") int limite,
			@RequestParam(defaultValue = "false") boolean incluirLecturas) {
		return consultar.recientes(limite, incluirLecturas).stream().map(Fila::de).toList();
	}

}
