package cl.ucn.alertacuida.application.aviso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.aviso.modelo.MensajeWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.PlantillaWhatsapp;
import cl.ucn.alertacuida.application.aviso.modelo.WhatsappPendiente;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Qué plantilla aprobada de Meta lleva cada WhatsApp y con qué valores (24-11-2026): el simulacro usa la de
 * prueba; una grave, la que pide llamar al 131; el resto, la leve. Los valores no llevan datos clínicos (solo
 * a quién, el folio y la gravedad) y los botones devuelven la respuesta en su payload.
 */
class PlantillasWhatsappTest {

	private static final PlantillaWhatsapp LEVE = new PlantillaWhatsapp("alerta_leve", "es",
			List.of("quien", "folio"), List.of("VOY_EN_CAMINO", "NO_PUEDO"));
	private static final PlantillaWhatsapp GRAVE = new PlantillaWhatsapp("alerta_grave", "es_CL",
			List.of("quien", "gravedad", "folio"), List.of("VOY_EN_CAMINO", "NO_PUEDO"));
	private static final PlantillaWhatsapp SIMULACRO = new PlantillaWhatsapp("simulacro", "es", List.of("folio"),
			List.of());

	private final PlantillasWhatsapp plantillas = new PlantillasWhatsapp(LEVE, GRAVE, SIMULACRO,
			"la persona que usted apoya");

	/** Una leve o media usa la plantilla leve, con el alias y el folio. */
	@Test
	void leveYMediaUsanLaPlantillaLeve() {
		MensajeWhatsapp leve = plantillas.armar(pendiente("Rosa", Severidad.LEVE, false));
		MensajeWhatsapp media = plantillas.armar(pendiente("Rosa", Severidad.MEDIA, false));

		assertThat(leve.plantilla()).isEqualTo("alerta_leve");
		assertThat(leve.idioma()).isEqualTo("es");
		assertThat(leve.telefono()).isEqualTo("+56911111111");
		assertThat(leve.valores()).containsExactly("Rosa", "#142");
		assertThat(leve.botones()).containsExactly("VOY_EN_CAMINO", "NO_PUEDO");
		assertThat(media.plantilla()).isEqualTo("alerta_leve");
	}

	/** Una grave usa la plantilla grave, con las variables en el orden configurado. */
	@Test
	void unaGraveUsaLaPlantillaGrave() {
		MensajeWhatsapp grave = plantillas.armar(pendiente("Rosa", Severidad.GRAVE, false));

		assertThat(grave.plantilla()).isEqualTo("alerta_grave");
		assertThat(grave.idioma()).isEqualTo("es_CL");
		assertThat(grave.valores()).containsExactly("Rosa", "grave", "#142");
	}

	/** Un simulacro usa la plantilla de prueba, cualquiera sea su gravedad. */
	@Test
	void unSimulacroUsaLaPlantillaDePrueba() {
		MensajeWhatsapp simulacro = plantillas.armar(pendiente("Rosa", Severidad.GRAVE, true));

		assertThat(simulacro.plantilla()).isEqualTo("simulacro");
		assertThat(simulacro.valores()).containsExactly("#142");
		assertThat(simulacro.botones()).isEmpty();
	}

	/** Sin alias, {quien} es el texto configurado: el nombre real nunca sale por WhatsApp. */
	@Test
	void sinAliasUsaElTextoConfigurado() {
		assertThat(plantillas.armar(pendiente(null, Severidad.LEVE, false)).valores())
				.containsExactly("la persona que usted apoya", "#142");
	}

	/** Una variable o un botón que no existe se rechaza al configurar, no al enviar. */
	@Test
	void unaVariableOBotonDesconocidoSeRechazaAlConfigurar() {
		assertThatThrownBy(() -> new PlantillasWhatsapp(new PlantillaWhatsapp("x", "es", List.of("diagnostico"),
				List.of()), GRAVE, SIMULACRO, "alguien")).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("diagnostico");
		assertThatThrownBy(() -> new PlantillasWhatsapp(new PlantillaWhatsapp("x", "es", List.of(),
				List.of("TAL_VEZ")), GRAVE, SIMULACRO, "alguien")).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("TAL_VEZ");
		assertThatThrownBy(() -> new PlantillasWhatsapp(new PlantillaWhatsapp(" ", "es", List.of(), List.of()),
				GRAVE, SIMULACRO, "alguien")).isInstanceOf(IllegalArgumentException.class);
	}

	/** @return un aviso a un contacto de la alerta #142 */
	private static WhatsappPendiente pendiente(String alias, Severidad severidad, boolean simulacro) {
		return new WhatsappPendiente(UUID.randomUUID(), "+56911111111", 142, alias, TipoEvento.CAIDA, severidad,
				simulacro, 0);
	}

}
