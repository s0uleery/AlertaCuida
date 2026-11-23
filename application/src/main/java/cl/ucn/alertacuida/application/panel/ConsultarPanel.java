package cl.ucn.alertacuida.application.panel;

import cl.ucn.alertacuida.application.panel.modelo.FilaPanel;
import cl.ucn.alertacuida.application.panel.modelo.Panel;
import cl.ucn.alertacuida.application.panel.modelo.RelojPanel;
import cl.ucn.alertacuida.application.panel.modelo.TotalesAlertas;
import cl.ucn.alertacuida.application.panel.modelo.TotalesPanel;
import cl.ucn.alertacuida.application.panel.modelo.UsuarioPanel;
import cl.ucn.alertacuida.application.panel.puerto.PanelUsuarios;
import cl.ucn.alertacuida.application.reloj.modelo.EstadoTecnicoReloj;
import cl.ucn.alertacuida.domain.reloj.ColorSemaforo;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Panel general de la portada: totales de alertas y de relojes con problemas, y una fila por usuario activo con
 * su reloj y su semáforo. El semáforo no se calcula aquí: se toma del estado técnico de los relojes, el mismo de
 * la lista de relojes.
 */
public class ConsultarPanel {

	/** Del semáforo más preocupante al menos; sin reloj va junto al gris. */
	private static final Map<ColorSemaforo, Integer> PRIORIDAD = Map.of(ColorSemaforo.ROJO, 0,
			ColorSemaforo.AMARILLO, 1, ColorSemaforo.GRIS, 2, ColorSemaforo.VERDE, 3);
	/** Lugar de un usuario sin reloj en el orden: junto al gris. */
	private static final int SIN_RELOJ = 2;

	/** Más alertas abiertas primero; después el semáforo más preocupante; después el apellido. */
	private static final Comparator<UsuarioPanel> ORDEN = Comparator
			.comparingInt(UsuarioPanel::alertasAbiertas).reversed()
			.thenComparingInt(ConsultarPanel::prioridad)
			.thenComparing(u -> u.apellidos() == null ? "" : u.apellidos());

	private final Supplier<List<EstadoTecnicoReloj>> estadosRelojes;
	private final PanelUsuarios usuarios;

	/**
	 * @param estadosRelojes el estado técnico de todos los relojes (ConsultarEstadoRelojes::consultar)
	 * @param usuarios       los usuarios activos y los totales de alertas
	 */
	public ConsultarPanel(Supplier<List<EstadoTecnicoReloj>> estadosRelojes, PanelUsuarios usuarios) {
		this.estadosRelojes = estadosRelojes;
		this.usuarios = usuarios;
	}

	/** @return los totales y los usuarios activos en orden */
	public Panel consultar() {
		List<EstadoTecnicoReloj> estados = estadosRelojes.get();
		Map<UUID, EstadoTecnicoReloj> porReloj = estados.stream()
				.collect(Collectors.toMap(EstadoTecnicoReloj::idDispositivo, Function.identity()));
		List<UsuarioPanel> filas = usuarios.usuariosActivos().stream().map(fila -> usuario(fila, porReloj))
				.sorted(ORDEN).toList();
		TotalesAlertas alertas = usuarios.totalesAlertas();
		return new Panel(new TotalesPanel(alertas.alertasAbiertas(), alertas.sinRespuesta(), alertas.enSeguimiento(),
				contar(estados, ColorSemaforo.ROJO), contar(estados, ColorSemaforo.AMARILLO)), filas);
	}

	/** @return la fila con el estado de su reloj; sin reloj (o sin estado), con el reloj vacío */
	private static UsuarioPanel usuario(FilaPanel fila, Map<UUID, EstadoTecnicoReloj> porReloj) {
		EstadoTecnicoReloj estado = fila.idDispositivo() == null ? null : porReloj.get(fila.idDispositivo());
		RelojPanel reloj = estado == null ? null : new RelojPanel(estado.idDispositivo(), fila.identificadorReloj(),
				estado.semaforo(), estado.motivos(), estado.ultimaSincronizacion(), estado.nivelBateria(),
				estado.tipoConexion(), estado.configuracionPendiente());
		return new UsuarioPanel(fila.id(), fila.nombres(), fila.apellidos(), reloj, fila.alertasAbiertas());
	}

	/** @return el lugar del usuario según su semáforo: 0 rojo, 1 amarillo, 2 gris o sin reloj, 3 verde */
	private static int prioridad(UsuarioPanel usuario) {
		return usuario.reloj() == null ? SIN_RELOJ : PRIORIDAD.get(usuario.reloj().semaforo());
	}

	/** @return cuántos relojes tienen ese color */
	private static int contar(List<EstadoTecnicoReloj> estados, ColorSemaforo color) {
		return (int) estados.stream().filter(e -> e.semaforo() == color).count();
	}

}
