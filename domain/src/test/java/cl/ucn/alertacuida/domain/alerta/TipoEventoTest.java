package cl.ucn.alertacuida.domain.alerta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

/**
 * Alertas técnicas y de salud (docs/alertas.md, "Tipos de alerta"; decisión del 10-11-2026): las de
 * salud las enruta el reloj a los contactos del paciente; las técnicas hablan del reloj y las avisa el
 * servidor al equipo PRLAC.
 */
class TipoEventoTest {

	/** Las técnicas son las cuatro que tratan del reloj, no de la persona. */
	@Test
	void lasTecnicasSonLasCuatroDelReloj() {
		assertEquals(EnumSet.of(TipoEvento.SILENCIO_DATOS, TipoEvento.BATERIA_BAJA, TipoEvento.RELOJ_NO_PUESTO,
				TipoEvento.EXCESO_ALERTAS), TipoEvento.tecnicas());
	}

	/** Las de salud no son técnicas. */
	@Test
	void lasDeSaludNoSonTecnicas() {
		for (TipoEvento tipo : EnumSet.of(TipoEvento.CAIDA, TipoEvento.FC, TipoEvento.SPO2, TipoEvento.FC_SPO2,
				TipoEvento.TENDENCIA, TipoEvento.INACTIVIDAD, TipoEvento.ZONA_SEGURA, TipoEvento.BOTON_AYUDA)) {
			assertFalse(tipo.esTecnica(), tipo.name());
		}
	}

	/** Cada técnica se reconoce como tal. */
	@Test
	void cadaTecnicaSeReconoce() {
		TipoEvento.tecnicas().forEach(tipo -> assertTrue(tipo.esTecnica(), tipo.name()));
	}

}
