package cl.ucn.alertacuida.application.calendario;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.calendario.puerto.HorariosHabiles;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.domain.calendario.CalendarioHabil;
import cl.ucn.alertacuida.domain.calendario.DiaSemana;
import cl.ucn.alertacuida.domain.calendario.TramoHabil;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cambia la jornada hábil del PRLAC desde el Calendario del dashboard (decisión del 25-11-2026). Se guarda en la
 * base y rige al tiro para todo lo que usa el horario hábil: qué alertas leves esperan al día hábil, el aviso a la
 * oficina, el vigilante y los correos al equipo. Los feriados no se tocan.
 */
public class GuardarHorarioHabil {

	private final HorariosHabiles horarios;
	private final CalendarioHabil calendario;
	private final RegistroAuditoria auditoria;
	private final Transacciones transacciones;
	private final Clock reloj;

	/** Recibe lo que usa; lo arma ConfiguracionCalendario. */
	public GuardarHorarioHabil(HorariosHabiles horarios, CalendarioHabil calendario, RegistroAuditoria auditoria,
			Transacciones transacciones, Clock reloj) {
		this.horarios = horarios;
		this.calendario = calendario;
		this.auditoria = auditoria;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/**
	 * @param tramos    inicio y término de cada día hábil; un día sin tramo (o null) no es hábil
	 * @param idUsuario quien lo cambia, para la auditoría
	 * @param ip        desde dónde llegó la petición
	 * @throws SolicitudRechazada si un día termina antes de empezar, le falta una hora o no queda ningún día hábil
	 */
	public void guardar(Map<DiaSemana, Tramo> tramos, UUID idUsuario, String ip) {
		Map<DiaSemana, TramoHabil> nuevo = new EnumMap<>(DiaSemana.class);
		tramos.forEach((dia, tramo) -> {
			if (tramo != null) {
				try {
					nuevo.put(dia, new TramoHabil(tramo.inicio(), tramo.fin()));
				} catch (IllegalArgumentException mal) {
					throw new SolicitudRechazada(nombre(dia) + ": " + mal.getMessage());
				}
			}
		});
		if (nuevo.isEmpty()) {
			throw new SolicitudRechazada("Debe quedar al menos un día hábil");
		}
		Map<DiaSemana, TramoHabil> anterior = calendario.horario();
		if (anterior.equals(nuevo)) {
			return;
		}
		Instant ahora = reloj.instant();
		transacciones.ejecutar(() -> {
			horarios.guardar(nuevo);
			Map<String, Object> detalle = new LinkedHashMap<>();
			detalle.put("anterior", texto(anterior));
			detalle.put("nuevo", texto(nuevo));
			auditoria.registrar(idUsuario, "MODIFICAR", "CALENDARIO", "horario_habil", ip, ahora, detalle);
			return null;
		});
		// Después de guardar: si la base falla, el horario en uso no cambia
		calendario.cambiarHorario(nuevo);
	}

	/** @return el horario como texto legible para la auditoría, ej. "LUNES 08:30-17:30" */
	private static Map<String, String> texto(Map<DiaSemana, TramoHabil> horario) {
		Map<String, String> texto = new LinkedHashMap<>();
		horario.forEach((dia, tramo) -> texto.put(dia.name(), tramo.inicio() + "-" + tramo.fin()));
		return texto;
	}

	/** @return el día en minúsculas para los mensajes */
	private static String nombre(DiaSemana dia) {
		String texto = dia.name().toLowerCase(java.util.Locale.ROOT);
		return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
	}

	/**
	 * El tramo de un día tal como llega del dashboard; se revisa al guardar.
	 *
	 * @param inicio hora de inicio
	 * @param fin    hora de término
	 */
	public record Tramo(LocalTime inicio, LocalTime fin) {
	}

}
