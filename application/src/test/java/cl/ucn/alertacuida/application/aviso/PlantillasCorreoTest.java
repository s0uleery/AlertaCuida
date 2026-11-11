package cl.ucn.alertacuida.application.aviso;

import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.application.aviso.modelo.CorreoPendiente;
import cl.ucn.alertacuida.application.aviso.modelo.Mensaje;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Plantillas mínimas del correo institucional. Requisitos del cronograma y de las decisiones del
 * 08-11 y el 10-11-2026:
 * <ul>
 * <li>Tres plantillas: silencio de datos, alerta (grave) para el equipo y aviso de respaldo a un
 * contacto del paciente.</li>
 * <li>Sin datos clínicos: solo el alias del paciente, el folio de la alerta y su gravedad.</li>
 * <li>Si el paciente no tiene alias, se usa el folio de la alerta.</li>
 * <li>En las graves se le indica al contacto que llame al 131 (nadie llama solo al SAMU).</li>
 * </ul>
 */
class PlantillasCorreoTest {

	private static final String DASHBOARD = "https://alertacuida.muni.cl";

	private final PlantillasCorreo plantillas = new PlantillasCorreo(DASHBOARD);

	/** Al equipo, una alerta grave lleva el folio, el alias, la gravedad y el enlace al dashboard. */
	@Test
	void alertaGraveAlEquipo() {
		Mensaje mensaje = plantillas.redactar(alEquipo(TipoEvento.CAIDA, Severidad.GRAVE, "Rosa"));

		assertThat(mensaje.destinatario()).isEqualTo("ana@muni.cl");
		assertThat(mensaje.asunto()).contains("grave", "#142", "Rosa");
		assertThat(mensaje.texto()).contains("#142", "Rosa", DASHBOARD);
	}

	/** El tipo de una alerta clínica es un dato clínico: no va en el correo, solo su gravedad. */
	@Test
	void alertaClinicaNoNombraLoDetectado() {
		Mensaje mensaje = plantillas.redactar(alEquipo(TipoEvento.FC_SPO2, Severidad.GRAVE, "Rosa"));

		String todo = (mensaje.asunto() + mensaje.texto()).toLowerCase();
		assertThat(todo).doesNotContain("caída", "fc", "spo2", "frecuencia", "oxígeno", "saturación");
	}

	/** El silencio de datos tiene su propia plantilla. */
	@Test
	void silencioDeDatosAlEquipo() {
		Mensaje mensaje = plantillas.redactar(alEquipo(TipoEvento.SILENCIO_DATOS, Severidad.MEDIA, "Rosa"));

		assertThat(mensaje.asunto()).contains("dejó de sincronizar", "Rosa");
		assertThat(mensaje.texto()).contains("#142", DASHBOARD);
	}

	/** Las técnicas (respaldo de un WhatsApp a la oficina) dicen qué le pasa al reloj. */
	@Test
	void alertaTecnicaDiceQuePasaConElReloj() {
		Mensaje mensaje = plantillas.redactar(alEquipo(TipoEvento.BATERIA_BAJA, Severidad.LEVE, "Rosa"));

		assertThat(mensaje.asunto()).contains("técnica", "batería baja", "#142");
	}

	/** Sin alias, el correo al equipo identifica al paciente solo por el folio, sin dejar huecos. */
	@Test
	void sinAliasAlEquipoBastaElFolio() {
		Mensaje mensaje = plantillas.redactar(alEquipo(TipoEvento.CAIDA, Severidad.GRAVE, null));

		assertThat(mensaje.asunto()).contains("#142").doesNotContain("null", " de  ", "de :");
		assertThat(mensaje.texto()).doesNotContain("null");
	}

	/** Al contacto de una alerta grave: comunicarse con la persona y, si no puede, llamar al 131. */
	@Test
	void contactoDeUnaGraveDebeLlamarAl131() {
		Mensaje mensaje = plantillas.redactar(aContacto(Severidad.GRAVE, "Rosa"));

		assertThat(mensaje.destinatario()).isEqualTo("hija@correo.cl");
		assertThat(mensaje.asunto()).contains("Rosa");
		assertThat(mensaje.texto()).contains("Rosa", "#142", "131");
	}

	/** En una media no se pide llamar al SAMU. */
	@Test
	void contactoDeUnaMediaSinSamu() {
		Mensaje mensaje = plantillas.redactar(aContacto(Severidad.MEDIA, "Rosa"));

		assertThat(mensaje.texto()).doesNotContain("131");
	}

	/** Sin alias, el contacto recibe el folio de la alerta en vez del nombre. */
	@Test
	void contactoSinAliasRecibeElFolio() {
		Mensaje mensaje = plantillas.redactar(aContacto(Severidad.GRAVE, "  "));

		assertThat(mensaje.asunto()).contains("#142");
		assertThat(mensaje.asunto() + mensaje.texto()).doesNotContain("null");
	}

	/** El contacto externo no recibe el enlace al dashboard, que es solo del equipo. */
	@Test
	void contactoNoRecibeElDashboard() {
		assertThat(plantillas.redactar(aContacto(Severidad.GRAVE, "Rosa")).texto()).doesNotContain(DASHBOARD);
	}

	/** @return un correo pendiente a una cuenta del equipo por la alerta #142 */
	private static CorreoPendiente alEquipo(TipoEvento tipo, Severidad severidad, String alias) {
		return new CorreoPendiente(UUID.randomUUID(), "ana@muni.cl", true, 142, alias, tipo, severidad, 0);
	}

	/** @return un correo pendiente a un contacto del paciente por una caída, la alerta #142 */
	private static CorreoPendiente aContacto(Severidad severidad, String alias) {
		return new CorreoPendiente(UUID.randomUUID(), "hija@correo.cl", false, 142, alias, TipoEvento.CAIDA,
				severidad, 0);
	}

}
