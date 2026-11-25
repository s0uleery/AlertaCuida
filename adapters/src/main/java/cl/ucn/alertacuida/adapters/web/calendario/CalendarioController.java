package cl.ucn.alertacuida.adapters.web.calendario;

import cl.ucn.alertacuida.adapters.seguridad.TokenJwt;
import cl.ucn.alertacuida.adapters.web.Permisos;
import cl.ucn.alertacuida.application.calendario.ConsultarCalendario;
import cl.ucn.alertacuida.application.calendario.GuardarHorarioHabil;
import cl.ucn.alertacuida.application.calendario.SimularEscalamiento;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.calendario.DiaSemana;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Calendario hábil del dashboard. Lo ve todo el equipo (basta con iniciar sesión). Desde el 25-11-2026 además se
 * edita la jornada hábil (ADMINISTRACION:EDITAR) y se simula qué pasaría con una alerta (ALERTAS:VER).
 */
@RestController
@RequestMapping("/api/calendario")
public class CalendarioController {

	private final ConsultarCalendario consultar;
	private final GuardarHorarioHabil guardarHorario;
	private final SimularEscalamiento simular;

	/** Recibe los casos de uso; los arma ConfiguracionCalendario. */
	public CalendarioController(ConsultarCalendario consultar, GuardarHorarioHabil guardarHorario,
			SimularEscalamiento simular) {
		this.consultar = consultar;
		this.guardarHorario = guardarHorario;
		this.simular = simular;
	}

	/** @return el horario de cada día hábil y los feriados. 401 sin sesión */
	@GetMapping
	public RespuestaCalendario consultar() {
		return RespuestaCalendario.de(consultar.consultar());
	}

	/**
	 * Cuerpo del cambio de jornada: el mismo formato que {@code horarioHabil} de la consulta.
	 *
	 * @param horarioHabil un tramo por cada día hábil; los días que no vienen dejan de ser hábiles
	 */
	public record SolicitudHorario(@NotNull List<@NotNull @Valid DiaHabil> horarioHabil) {
	}

	/**
	 * Un día hábil.
	 *
	 * @param inicio "08:30"
	 * @param fin    "17:30"
	 */
	public record DiaHabil(@NotNull DiaSemana dia, @NotNull String inicio, @NotNull String fin) {
	}

	/**
	 * Cambia la jornada hábil. Rige al tiro para el enrutamiento, el aviso a la oficina y el vigilante.
	 *
	 * @return 204. 400 si una hora está mal escrita, un día termina antes de empezar o no queda ningún día hábil
	 */
	@PutMapping("/horario")
	@PreAuthorize(Permisos.ADMINISTRACION_EDITAR)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void guardarHorario(@Valid @RequestBody SolicitudHorario solicitud, @AuthenticationPrincipal Jwt jwt,
			HttpServletRequest peticion) {
		Map<DiaSemana, GuardarHorarioHabil.Tramo> tramos = new EnumMap<>(DiaSemana.class);
		for (DiaHabil dia : solicitud.horarioHabil()) {
			if (tramos.put(dia.dia(), new GuardarHorarioHabil.Tramo(hora(dia.inicio()), hora(dia.fin()))) != null) {
				throw new SolicitudRechazada("El día " + dia.dia() + " viene dos veces");
			}
		}
		guardarHorario.guardar(tramos, TokenJwt.leer(jwt).id(), peticion.getRemoteAddr());
	}

	/**
	 * Qué pasaría con una alerta de salud de esa gravedad, ese día y a esa hora, con las reglas de hoy.
	 *
	 * @param fecha      "2026-11-25"
	 * @param hora       "15:30", hora de Chile
	 * @param gravedad   LEVE, MEDIA o GRAVE
	 * @param idPaciente opcional: muestra a qué contactos suyos avisaría el reloj
	 * @return la simulación. 400 si la fecha o la hora están mal escritas
	 */
	@GetMapping("/simulacion")
	@PreAuthorize(Permisos.ALERTAS_VER)
	public SimularEscalamiento.Simulacion simular(@RequestParam String fecha, @RequestParam String hora,
			@RequestParam Severidad gravedad, @RequestParam(required = false) UUID idPaciente) {
		LocalDate dia;
		try {
			dia = LocalDate.parse(fecha);
		} catch (DateTimeParseException mal) {
			throw new SolicitudRechazada("La fecha debe escribirse como 2026-11-25");
		}
		return simular.simular(dia, hora(hora), gravedad, idPaciente);
	}

	/** @return la hora escrita como "08:30" */
	private static LocalTime hora(String texto) {
		try {
			return LocalTime.parse(texto.strip());
		} catch (DateTimeParseException mal) {
			throw new SolicitudRechazada("La hora debe escribirse como 08:30: " + texto);
		}
	}

}
