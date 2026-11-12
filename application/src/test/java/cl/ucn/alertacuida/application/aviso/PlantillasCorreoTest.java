package cl.ucn.alertacuida.application.aviso;

import static cl.ucn.alertacuida.application.soporte.TextosCorreoDePrueba.textos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.aviso.modelo.CorreoPendiente;
import cl.ucn.alertacuida.application.aviso.modelo.Mensaje;
import cl.ucn.alertacuida.application.aviso.modelo.TextosCorreo;
import cl.ucn.alertacuida.application.respuesta.puerto.FirmaRespuesta;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Cómo se rellenan las plantillas del correo con los textos editables (decisión del 11-11-2026: los textos
 * van en un archivo YAML, con marcadores). Usa textos de prueba cortos; que los textos reales cumplan los
 * requisitos del contenido (sin datos clínicos, 131 en las graves) lo prueba TextosCorreoRealesTest.
 * <ul>
 * <li>R1 Cada marcador se reemplaza por su valor; escrito con mayúscula inicial, el valor parte con
 * mayúscula.</li>
 * <li>R2 Sin alias se usan los textos "sin alias", sin dejar huecos.</li>
 * <li>R3 Un párrafo que queda vacío (la línea del 131 en una alerta no grave) no deja líneas en blanco de
 * más.</li>
 * <li>R5 Las graves van marcadas como importantes; las demás, normales (decisión del 11-11-2026).</li>
 * <li>R4 Un marcador desconocido, un texto que falta, una gravedad o una alerta técnica sin nombre impiden
 * crear las plantillas: así el backend no arranca y nunca sale un correo con un marcador sin rellenar.</li>
 * <li>R6 El correo al contacto lleva un solo enlace a la página /responder del dashboard, donde elige
 * "Confirmo que voy" o "No puedo asistir", con el token firmado de ese aviso después de "#" (decisión del
 * 12-11-2026). Sin ese marcador el backend no arranca. Los correos al equipo no lo llevan.</li>
 * </ul>
 */
class PlantillasCorreoTest {

	private static final String DASHBOARD = "https://alertacuida.muni.cl";

	/** El aviso de los correos a contactos de estas pruebas. */
	private static final UUID INTENTO = UUID.fromString("00000000-0000-0000-0000-000000000142");

	/** La línea del enlace del texto de prueba, ya rellena para {@link #INTENTO}. */
	private static final String ENLACES = "Responder: " + DASHBOARD + "/responder#firmado-" + INTENTO;

	/** R1: los marcadores del asunto y del cuerpo se rellenan, incluidos los anidados como {alias}. */
	@Test
	void rellenaLosMarcadores() {
		Mensaje mensaje = plantillas(textos()).redactar(alEquipo(TipoEvento.CAIDA, Severidad.GRAVE, "Rosa"));

		assertThat(mensaje.destinatario()).isEqualTo("ana@muni.cl");
		assertThat(mensaje.asunto()).isEqualTo("Alerta: alerta grave #142 de Rosa");
		assertThat(mensaje.texto()).isEqualTo("Hay una alerta grave #142 de Rosa.\n\nVer: " + DASHBOARD
				+ "\n\nPie del equipo.");
	}

	/** R1: una alerta técnica usa su propio texto, con el nombre de lo que le pasa al reloj. */
	@Test
	void alertaTecnicaUsaSuNombre() {
		Mensaje mensaje = plantillas(textos()).redactar(alEquipo(TipoEvento.BATERIA_BAJA, Severidad.LEVE, "Rosa"));

		assertThat(mensaje.asunto()).isEqualTo("Alerta: alerta técnica #142 (batería baja) de Rosa");
	}

	/** R1: {Reloj} pone en mayúscula la primera letra del valor de {reloj}. */
	@Test
	void marcadorConMayusculaInicial() {
		Mensaje mensaje = plantillas(textos()).redactar(alEquipo(TipoEvento.SILENCIO_DATOS, Severidad.MEDIA, "Rosa"));

		assertThat(mensaje.asunto()).isEqualTo("Silencio: el reloj de Rosa (#142)");
		assertThat(mensaje.texto()).startsWith("El reloj de Rosa dejó de enviar datos.");
	}

	/** R2: sin alias, cada plantilla usa su texto alternativo y no quedan huecos. */
	@Test
	void sinAliasUsaLosTextosAlternativos() {
		PlantillasCorreo plantillas = plantillas(textos());

		assertThat(plantillas.redactar(alEquipo(TipoEvento.CAIDA, Severidad.GRAVE, null)).asunto())
				.isEqualTo("Alerta: alerta grave #142");
		assertThat(plantillas.redactar(alEquipo(TipoEvento.SILENCIO_DATOS, Severidad.MEDIA, " ")).texto())
				.startsWith("Un reloj dejó de enviar datos.");
		assertThat(plantillas.redactar(aContacto(Severidad.GRAVE, null)).asunto())
				.isEqualTo("Ayuda: la persona a su cuidado (#142)");
	}

	/** R3: en una grave va la línea del 131 como párrafo propio. */
	@Test
	void graveAgregaSuParrafo() {
		Mensaje mensaje = plantillas(textos()).redactar(aContacto(Severidad.GRAVE, "Rosa"));

		assertThat(mensaje.destinatario()).isEqualTo("hija@correo.cl");
		assertThat(mensaje.texto())
				.isEqualTo("Alerta de Rosa.\n\nLlame al 131.\n\n" + ENLACES + "\n\nPie del contacto.");
	}

	/** R3: en una media el párrafo del 131 desaparece sin dejar líneas en blanco de más. */
	@Test
	void parrafoVacioNoDejaLineasDeMas() {
		Mensaje mensaje = plantillas(textos()).redactar(aContacto(Severidad.MEDIA, "Rosa"));

		assertThat(mensaje.texto()).isEqualTo("Alerta de Rosa.\n\n" + ENLACES + "\n\nPie del contacto.");
	}

	/** R6: un solo enlace, con el token firmado de ese aviso después de "#". */
	@Test
	void contactoRecibeElEnlaceDeRespuesta() {
		CorreoPendiente correo = aContacto(Severidad.MEDIA, "Rosa");

		String texto = plantillas(textos()).redactar(correo).texto();

		assertThat(texto).contains("Responder: " + DASHBOARD + "/responder#firmado-" + correo.idIntento());
		assertThat(texto.split("/responder", -1)).hasSize(2);
	}

	/** R6: el correo al equipo no lleva el enlace de respuesta: solo un contacto del paciente confirma. */
	@Test
	void equipoNoPuedeUsarLosEnlaces() {
		TextosCorreo base = textos();
		TextosCorreo.AlertaEquipo a = base.alertaEquipo();
		TextosCorreo malo = new TextosCorreo(base.pieEquipo(), base.pieContacto(), base.silencioDatos(),
				new TextosCorreo.AlertaEquipo(a.asunto(), a.cuerpo() + "{responder}", a.alertaSalud(),
						a.alertaTecnica(), a.deQuienConAlias()),
				base.contacto(), base.gravedades(), base.tecnicas());

		assertThatThrownBy(() -> plantillas(malo))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("{responder}");
	}

	/** R6: sin el enlace en el cuerpo del contacto, las plantillas no se crean. */
	@Test
	void contactoSinEnlaceImpideCrearLasPlantillas() {
		TextosCorreo malo = conContacto(new TextosCorreo.Contacto("Ayuda {quien}", "Hola.\n{pie}", "x", "y"));

		assertThatThrownBy(() -> plantillas(malo))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("{responder}");
	}

	/** R5: las graves, al equipo o al contacto, van marcadas como importantes. */
	@Test
	void gravesVanComoImportantes() {
		PlantillasCorreo plantillas = plantillas(textos());

		assertThat(plantillas.redactar(alEquipo(TipoEvento.CAIDA, Severidad.GRAVE, "Rosa")).importante()).isTrue();
		assertThat(plantillas.redactar(aContacto(Severidad.GRAVE, "Rosa")).importante()).isTrue();
	}

	/** R5: las demás (medias, leves, técnicas y silencio de datos) van normales. */
	@Test
	void lasDemasNoVanComoImportantes() {
		PlantillasCorreo plantillas = plantillas(textos());

		assertThat(plantillas.redactar(aContacto(Severidad.MEDIA, "Rosa")).importante()).isFalse();
		assertThat(plantillas.redactar(alEquipo(TipoEvento.BATERIA_BAJA, Severidad.LEVE, "Rosa")).importante())
				.isFalse();
		assertThat(plantillas.redactar(alEquipo(TipoEvento.SILENCIO_DATOS, Severidad.MEDIA, "Rosa")).importante())
				.isFalse();
	}

	/** R4: un marcador mal escrito se informa con su nombre y su plantilla. */
	@Test
	void marcadorDesconocidoImpideCrearLasPlantillas() {
		TextosCorreo malo = conContacto(new TextosCorreo.Contacto("Ayuda {folo}", "{pie}", "x", "y"));

		assertThatThrownBy(() -> plantillas(malo))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("{folo}")
				.hasMessageContaining("contacto");
	}

	/** R4: un marcador que existe en otra plantilla no sirve en esta. */
	@Test
	void marcadorDeOtraPlantillaImpideCrearlas() {
		TextosCorreo malo = conContacto(new TextosCorreo.Contacto("Ayuda {dashboard}", "{pie}", "x", "y"));

		assertThatThrownBy(() -> plantillas(malo))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("{dashboard}");
	}

	/** R4: un texto vacío o que falta se informa. */
	@Test
	void textoQueFaltaImpideCrearLasPlantillas() {
		TextosCorreo malo = conContacto(new TextosCorreo.Contacto(" ", "{pie}", "x", "y"));

		assertThatThrownBy(() -> plantillas(malo))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("contacto");
	}

	/** R4: cada gravedad necesita su nombre. */
	@Test
	void gravedadSinNombreImpideCrearLasPlantillas() {
		TextosCorreo base = textos();
		Map<Severidad, String> gravedades = new HashMap<>(base.gravedades());
		gravedades.remove(Severidad.MEDIA);
		TextosCorreo malo = new TextosCorreo(base.pieEquipo(), base.pieContacto(), base.silencioDatos(),
				base.alertaEquipo(), base.contacto(), gravedades, base.tecnicas());

		assertThatThrownBy(() -> plantillas(malo))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("MEDIA");
	}

	/** R4: cada alerta técnica que va por la plantilla de alerta necesita su nombre. */
	@Test
	void tecnicaSinNombreImpideCrearLasPlantillas() {
		TextosCorreo base = textos();
		Map<TipoEvento, String> tecnicas = new HashMap<>(base.tecnicas());
		tecnicas.remove(TipoEvento.EXCESO_ALERTAS);
		TextosCorreo malo = new TextosCorreo(base.pieEquipo(), base.pieContacto(), base.silencioDatos(),
				base.alertaEquipo(), base.contacto(), base.gravedades(), tecnicas);

		assertThatThrownBy(() -> plantillas(malo))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("EXCESO_ALERTAS");
	}

	/** @return las plantillas con esos textos, el dashboard de prueba y una firma que antepone "firmado-" */
	private static PlantillasCorreo plantillas(TextosCorreo textos) {
		FirmaRespuesta firma = new FirmaRespuesta() {
			@Override
			public String firmar(UUID idIntento) {
				return "firmado-" + idIntento;
			}

			@Override
			public Optional<UUID> verificar(String token) {
				return Optional.empty();
			}
		};
		return new PlantillasCorreo(DASHBOARD, textos, firma);
	}

	/** @return los textos de prueba con otra plantilla para el contacto */
	private static TextosCorreo conContacto(TextosCorreo.Contacto contacto) {
		TextosCorreo base = textos();
		return new TextosCorreo(base.pieEquipo(), base.pieContacto(), base.silencioDatos(), base.alertaEquipo(),
				contacto, base.gravedades(), base.tecnicas());
	}

	/** @return un correo pendiente a una cuenta del equipo por la alerta #142 */
	private static CorreoPendiente alEquipo(TipoEvento tipo, Severidad severidad, String alias) {
		return new CorreoPendiente(UUID.randomUUID(), "ana@muni.cl", true, 142, alias, tipo, severidad, 0);
	}

	/** @return un correo pendiente a un contacto del paciente por una caída, la alerta #142 */
	private static CorreoPendiente aContacto(Severidad severidad, String alias) {
		return new CorreoPendiente(INTENTO, "hija@correo.cl", false, 142, alias, TipoEvento.CAIDA,
				severidad, 0);
	}

}
