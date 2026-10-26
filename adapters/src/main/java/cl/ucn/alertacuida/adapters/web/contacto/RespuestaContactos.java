package cl.ucn.alertacuida.adapters.web.contacto;

import cl.ucn.alertacuida.application.contacto.modelo.ContactoDelPaciente;
import cl.ucn.alertacuida.application.contacto.modelo.ListaContactos;
import cl.ucn.alertacuida.domain.contacto.Canal;
import cl.ucn.alertacuida.domain.contacto.Cobertura;
import cl.ucn.alertacuida.domain.contacto.Horario;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta de {@code GET /api/pacientes/{id}/contactos}: la lista con las horas sin cobertura.
 *
 * @param contactos    activos por prioridad y luego desactivados
 * @param sinCobertura tramos sin ningún contacto disponible ("HH:mm"; inicio igual a fin = el día completo)
 */
public record RespuestaContactos(List<Contacto> contactos, List<Tramo> sinCobertura) {

	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

	/**
	 * Un contacto, en plano para el dashboard.
	 *
	 * @param id                identificador del vínculo
	 * @param nombre            de la persona
	 * @param whatsapp          número normalizado, o vacío
	 * @param correo            correo normalizado, o vacío
	 * @param optInVigente      si se le puede enviar WhatsApp
	 * @param fechaOptIn        cuándo lo aceptó, o vacío
	 * @param fechaRevocacion   cuándo lo revocó, o vacío
	 * @param tipo              código del tipo de contacto
	 * @param parentesco        solo en familiares
	 * @param cuidadorPrincipal si es el cuidador principal
	 * @param prioridad         orden en que avisa el reloj
	 * @param horaInicio        "HH:mm", o vacío si es todo el día
	 * @param horaFin           "HH:mm", o vacío si es todo el día
	 * @param activo            false si está desactivado
	 * @param otrosPacientes    de cuántos otros pacientes es contacto activo
	 * @param canales           los que el servidor puede usar hoy
	 */
	public record Contacto(UUID id, String nombre, String whatsapp, String correo, boolean optInVigente,
			Instant fechaOptIn, Instant fechaRevocacion, String tipo, String parentesco, boolean cuidadorPrincipal,
			int prioridad, String horaInicio, String horaFin, boolean activo, int otrosPacientes,
			List<Canal> canales) {
	}

	/**
	 * Tramo del día sin cobertura.
	 *
	 * @param inicio "HH:mm"
	 * @param fin    "HH:mm"
	 */
	public record Tramo(String inicio, String fin) {
	}

	/** @return la respuesta armada desde la lista del caso de uso */
	public static RespuestaContactos de(ListaContactos lista) {
		return new RespuestaContactos(lista.contactos().stream().map(RespuestaContactos::contacto).toList(),
				lista.sinCobertura().stream().map(RespuestaContactos::tramo).toList());
	}

	/** @return el contacto en plano */
	private static Contacto contacto(ContactoDelPaciente c) {
		Horario horario = c.vinculo().horario();
		return new Contacto(c.id(), c.persona().nombre(), c.persona().whatsapp(), c.persona().correo(),
				c.persona().optInVigente(), c.persona().fechaOptIn(), c.persona().fechaRevocacion(),
				c.vinculo().tipo(), c.vinculo().parentesco(), c.vinculo().cuidadorPrincipal(), c.prioridad(),
				horario == null ? null : hora(horario.inicio()), horario == null ? null : hora(horario.fin()),
				c.activo(), c.otrosPacientes(), c.canales());
	}

	/** @return el tramo con sus horas en "HH:mm" */
	private static Tramo tramo(Cobertura.Tramo tramo) {
		return new Tramo(hora(tramo.inicio()), hora(tramo.fin()));
	}

	/** @return la hora en "HH:mm" */
	private static String hora(LocalTime hora) {
		return hora.format(HORA);
	}

}
