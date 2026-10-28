package cl.ucn.alertacuida.adapters.web.dispositivo;

import cl.ucn.alertacuida.application.clinica.modelo.Modulos;
import cl.ucn.alertacuida.application.configuracion.modelo.ConfiguracionReloj;
import cl.ucn.alertacuida.application.configuracion.modelo.ContenidoConfiguracion;
import cl.ucn.alertacuida.domain.calendario.DiaSemana;
import cl.ucn.alertacuida.domain.clinica.Umbral;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import cl.ucn.alertacuida.domain.contacto.Canal;
import cl.ucn.alertacuida.domain.contacto.Horario;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Cuerpo de {@code GET /api/reloj/v1/configuracion} (esquema Configuracion de docs/api-reloj.yaml).
 * Las horas van como "08:30", en hora de Chile.
 */
public record RespuestaConfiguracion(String version, Modulos modulos, boolean gpsActivo, boolean modoConservador,
		Umbrales umbrales, Reglas reglas, Escalamiento escalamiento, List<Contacto> contactos,
		Calendario calendario) {

	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

	/** Umbrales de FC y SpO2. */
	public record Umbrales(Fc fc, Spo2 spo2) {
	}

	/** graveBajo &lt; normalMin &lt; normalMax &lt; graveAlto. */
	public record Fc(BigDecimal graveBajo, BigDecimal normalMin, BigDecimal normalMax, BigDecimal graveAlto) {
	}

	/** Sin límite alto. */
	public record Spo2(BigDecimal graveBajo, BigDecimal normalMin) {
	}

	/** Por ahora solo la versión de la matriz FC × SpO2; los parámetros esperan el Anexo B. */
	public record Reglas(String versionMatriz) {
	}

	/** Plazo antes de pasar al siguiente contacto y veces que se recorre la lista. */
	public record Escalamiento(int minutosEspera, int rondas) {
	}

	/**
	 * Un contacto, sin número ni correo.
	 *
	 * @param horario vacío = todo el día; si inicio &gt; fin, cruza la medianoche
	 */
	public record Contacto(UUID idContacto, String nombre, int prioridad, Tramo horario, List<Canal> canales) {
	}

	/** Un tramo horario: desde "inicio", incluida, hasta "fin", sin incluirla. */
	public record Tramo(String inicio, String fin) {
	}

	/** Un día hábil con su horario. */
	public record Dia(DiaSemana dia, String inicio, String fin) {
	}

	/**
	 * Horario hábil y feriados.
	 *
	 * @param horarioHabil los días que no aparecen no son hábiles
	 * @param feriados     del año en curso y el siguiente
	 */
	public record Calendario(String zonaHoraria, List<Dia> horarioHabil, List<LocalDate> feriados) {
	}

	/** @return la configuración con los nombres del contrato */
	static RespuestaConfiguracion de(ConfiguracionReloj configuracion) {
		ContenidoConfiguracion c = configuracion.contenido();
		Umbral fc = c.fc();
		Umbral spo2 = c.spo2();
		return new RespuestaConfiguracion(configuracion.version(), c.modulos(), c.gpsActivo(), c.modoConservador(),
				new Umbrales(new Fc(fc.graveBajo(), fc.normalMin(), fc.normalMax(), fc.graveAlto()),
						new Spo2(spo2.graveBajo(), spo2.normalMin())),
				new Reglas(c.versionMatriz()),
				new Escalamiento(c.minutosEspera(), c.rondas()),
				c.contactos().stream()
						.map(contacto -> new Contacto(contacto.idContacto(), contacto.nombre(), contacto.prioridad(),
								tramo(contacto.horario()), contacto.canales()))
						.toList(),
				new Calendario(ZonaHoraria.CHILE.getId(),
						c.horarioHabil().entrySet().stream()
								.map(dia -> new Dia(dia.getKey(), hora(dia.getValue().inicio()),
										hora(dia.getValue().fin())))
								.toList(),
						c.feriados()));
	}

	/** @return el horario del contacto, o vacío si recibe avisos todo el día */
	private static Tramo tramo(Horario horario) {
		return horario == null ? null : new Tramo(hora(horario.inicio()), hora(horario.fin()));
	}

	private static String hora(LocalTime hora) {
		return hora.format(HORA);
	}

}
