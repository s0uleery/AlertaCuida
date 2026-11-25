package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.calendario.ConsultarCalendario;
import cl.ucn.alertacuida.application.calendario.GuardarHorarioHabil;
import cl.ucn.alertacuida.application.calendario.ReglaEnrutamiento;
import cl.ucn.alertacuida.application.calendario.SimularEscalamiento;
import cl.ucn.alertacuida.application.calendario.puerto.HorariosHabiles;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.contacto.puerto.ListaDelPaciente;
import java.time.Clock;
import cl.ucn.alertacuida.domain.calendario.CalendarioHabil;
import cl.ucn.alertacuida.domain.calendario.DiaSemana;
import cl.ucn.alertacuida.domain.calendario.Feriado;
import cl.ucn.alertacuida.domain.calendario.TramoHabil;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Calendario hábil de calendario.yml y los casos de uso que lo usan. Igual que
 * {@link ConfiguracionPerfilYContactos}: si el calendario está mal escrito, el backend no arranca.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesCalendario.class)
public class ConfiguracionCalendario {

	/**
	 * Horario y feriados ya convertidos y revisados.
	 *
	 * @throws IllegalArgumentException si un día, una hora o una fecha está mal escrita, con la
	 *                                  propiedad que hay que revisar
	 */
	@Bean
	CalendarioHabil calendarioHabil(PropiedadesCalendario propiedades, HorariosHabiles horarios) {
		Map<DiaSemana, TramoHabil> delArchivo = new EnumMap<>(DiaSemana.class);
		propiedades.horario().forEach((dia, tramo) -> {
			String propiedad = "alertacuida.calendario.horario." + dia;
			delArchivo.put(dia(dia, propiedad), new TramoHabil(hora(tramo.inicio(), propiedad + ".inicio"),
					hora(tramo.fin(), propiedad + ".fin")));
		});
		List<Feriado> feriados = propiedades.feriados().entrySet().stream()
				.map(feriado -> new Feriado(fecha(feriado.getKey()), feriado.getValue()))
				.toList();
		// Desde el 25-11-2026 el horario lo edita el equipo y vive en la base; calendario.yml solo la siembra
		Map<DiaSemana, TramoHabil> guardado = horarios.leer();
		if (guardado.isEmpty()) {
			horarios.guardar(delArchivo);
			guardado = delArchivo;
		}
		return CalendarioHabil.de(guardado, feriados);
	}

	/** Simulador de canales del Calendario: las mismas reglas que aplica el reloj. */
	@Bean
	SimularEscalamiento simularEscalamiento(ReglaEnrutamiento regla, CalendarioHabil calendario,
			ListaDelPaciente contactos) {
		return new SimularEscalamiento(regla, calendario, contactos);
	}

	/** Cambio de la jornada hábil desde el Calendario del dashboard. */
	@Bean
	GuardarHorarioHabil guardarHorarioHabil(HorariosHabiles horarios, CalendarioHabil calendario,
			RegistroAuditoria auditoria, Transacciones transacciones, Clock reloj) {
		return new GuardarHorarioHabil(horarios, calendario, auditoria, transacciones, reloj);
	}

	/** Regla de gravedad y horario hábil. */
	@Bean
	ReglaEnrutamiento reglaEnrutamiento(CalendarioHabil calendario) {
		return new ReglaEnrutamiento(calendario);
	}

	/** Calendario para el dashboard. */
	@Bean
	ConsultarCalendario consultarCalendario(CalendarioHabil calendario) {
		return new ConsultarCalendario(calendario);
	}

	/** @return el día escrito en español ("lunes", "miercoles") */
	private static DiaSemana dia(String texto, String propiedad) {
		try {
			return DiaSemana.valueOf(texto.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(propiedad + ": el día debe ser lunes, martes, miercoles, jueves, "
					+ "viernes, sabado o domingo", e);
		}
	}

	/** @return la hora escrita como "08:30" */
	private static LocalTime hora(String texto, String propiedad) {
		try {
			return LocalTime.parse(texto.strip());
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException(propiedad + ": la hora debe escribirse como 08:30", e);
		}
	}

	/** @return la fecha escrita como "2026-09-18" */
	private static LocalDate fecha(String texto) {
		try {
			return LocalDate.parse(texto);
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException("alertacuida.calendario.feriados." + texto
					+ ": la fecha debe escribirse como 2026-09-18", e);
		}
	}

}
