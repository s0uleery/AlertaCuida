package cl.ucn.alertacuida.application.panel;

import static org.assertj.core.api.Assertions.assertThat;

import cl.ucn.alertacuida.application.panel.modelo.FilaPanel;
import cl.ucn.alertacuida.application.panel.modelo.Panel;
import cl.ucn.alertacuida.application.panel.modelo.TotalesAlertas;
import cl.ucn.alertacuida.application.panel.modelo.UsuarioPanel;
import cl.ucn.alertacuida.application.panel.puerto.PanelUsuarios;
import cl.ucn.alertacuida.application.reloj.modelo.EstadoTecnicoReloj;
import cl.ucn.alertacuida.domain.reloj.ColorSemaforo;
import cl.ucn.alertacuida.domain.reloj.MotivoSemaforo;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Panel general de la portada (decisiones del 19-11-2026): totales de alertas y de relojes en rojo y amarillo,
 * y una fila por usuario activo con su reloj y su semáforo. Primero los que tienen más alertas abiertas, después
 * el semáforo más preocupante y por último el apellido.
 */
class ConsultarPanelTest {

	private static final UUID RELOJ_ROJO = UUID.randomUUID();
	private static final UUID RELOJ_VERDE = UUID.randomUUID();
	private static final UUID RELOJ_AMARILLO = UUID.randomUUID();
	private static final UUID RELOJ_LIBRE = UUID.randomUUID();

	private final List<EstadoTecnicoReloj> estados = List.of(estado(RELOJ_ROJO, ColorSemaforo.ROJO),
			estado(RELOJ_VERDE, ColorSemaforo.VERDE), estado(RELOJ_AMARILLO, ColorSemaforo.AMARILLO),
			estado(RELOJ_LIBRE, ColorSemaforo.GRIS));

	/** Los totales de alertas vienen del puerto; los de relojes se cuentan con el semáforo de todos los relojes. */
	@Test
	void losTotales() {
		Panel panel = new ConsultarPanel(() -> estados, puerto(List.of())).consultar();

		assertThat(panel.totales().alertasAbiertas()).isEqualTo(5);
		assertThat(panel.totales().sinRespuesta()).isEqualTo(1);
		assertThat(panel.totales().enSeguimiento()).isEqualTo(2);
		assertThat(panel.totales().relojesRojo()).isEqualTo(1);
		assertThat(panel.totales().relojesAmarillo()).isEqualTo(1);
	}

	/** Cada usuario lleva el estado de su reloj; sin reloj, vacío. */
	@Test
	void cadaUsuarioConSuReloj() {
		Panel panel = new ConsultarPanel(() -> estados,
				puerto(List.of(fila("Rojas", RELOJ_ROJO, 0), fila("Sin Reloj", null, 0)))).consultar();

		UsuarioPanel conReloj = buscar(panel, "Rojas");
		assertThat(conReloj.reloj().identificador()).isEqualTo("R-Rojas");
		assertThat(conReloj.reloj().semaforo()).isEqualTo(ColorSemaforo.ROJO);
		assertThat(conReloj.reloj().motivos()).containsExactly(MotivoSemaforo.SIN_SENAL);
		assertThat(buscar(panel, "Sin Reloj").reloj()).isNull();
	}

	/** Orden: más alertas abiertas; a igualdad, rojo, amarillo, sin reloj o gris, verde; después el apellido. */
	@Test
	void elOrden() {
		Panel panel = new ConsultarPanel(() -> estados, puerto(List.of(fila("Verde", RELOJ_VERDE, 0),
				fila("Zúñiga", RELOJ_AMARILLO, 0), fila("Alarcón", RELOJ_AMARILLO, 0), fila("Sin Reloj", null, 0),
				fila("Rojo", RELOJ_ROJO, 0), fila("Con Alertas", RELOJ_VERDE, 2)))).consultar();

		assertThat(panel.usuarios()).extracting(UsuarioPanel::apellidos)
				.containsExactly("Con Alertas", "Rojo", "Alarcón", "Zúñiga", "Sin Reloj", "Verde");
	}

	/** @return el usuario con ese apellido */
	private static UsuarioPanel buscar(Panel panel, String apellidos) {
		return panel.usuarios().stream().filter(u -> u.apellidos().equals(apellidos)).findFirst().orElseThrow();
	}

	/** @return una fila con ese apellido, ese reloj (o ninguno) y esas alertas abiertas */
	private static FilaPanel fila(String apellidos, UUID reloj, int alertas) {
		return new FilaPanel(UUID.randomUUID(), "Nombre", apellidos, reloj, reloj == null ? null : "R-" + apellidos,
				alertas);
	}

	/** @return el estado técnico de un reloj con ese color */
	private static EstadoTecnicoReloj estado(UUID reloj, ColorSemaforo color) {
		List<MotivoSemaforo> motivos = color == ColorSemaforo.ROJO ? List.of(MotivoSemaforo.SIN_SENAL) : List.of();
		return new EstadoTecnicoReloj(reloj, color, motivos, null, null, null, null, null, false);
	}

	/** @return un puerto con esas filas y totales fijos de alertas */
	private static PanelUsuarios puerto(List<FilaPanel> filas) {
		return new PanelUsuarios() {

			@Override
			public List<FilaPanel> usuariosActivos() {
				return filas;
			}

			@Override
			public TotalesAlertas totalesAlertas() {
				return new TotalesAlertas(5, 1, 2);
			}

		};
	}

}
