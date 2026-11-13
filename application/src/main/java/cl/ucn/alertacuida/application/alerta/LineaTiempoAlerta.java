package cl.ucn.alertacuida.application.alerta;

import cl.ucn.alertacuida.application.alerta.modelo.AccionSeguimiento;
import cl.ucn.alertacuida.application.alerta.modelo.AvisoRelojRegistrado;
import cl.ucn.alertacuida.application.alerta.modelo.HistorialAlerta;
import cl.ucn.alertacuida.application.alerta.modelo.IntentoRegistrado;
import cl.ucn.alertacuida.application.alerta.modelo.PasoAlerta;
import cl.ucn.alertacuida.application.alerta.modelo.TipoPaso;
import cl.ucn.alertacuida.application.aviso.modelo.PoliticaReintentos;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Arma la línea de tiempo de una alerta con las fechas registradas.
 *
 * <p>Los pasos se agregan en orden lógico y se ordenan por fecha con un orden estable: si dos tienen la
 * misma hora, queda primero el que ocurre antes (el salto, antes del aviso; el cierre, antes del aviso
 * al reloj).
 */
final class LineaTiempoAlerta {

	/** Solo funciones: no se crean objetos de esta clase. */
	private LineaTiempoAlerta() {
	}

	/**
	 * @param politica reintentos de los avisos al reloj, para saber cuáles se agotaron
	 * @param ahora    hora actual
	 * @return los pasos de la alerta, del más antiguo al más reciente
	 */
	static List<PasoAlerta> de(HistorialAlerta historial, PoliticaReintentos politica, Instant ahora) {
		List<PasoAlerta> pasos = new ArrayList<>();
		agregarDelReloj(pasos, historial);
		agregarAvisos(pasos, historial.intentos());
		agregarAtencion(pasos, historial);
		agregarAvisosReloj(pasos, historial, politica, ahora);
		agregarSimple(pasos, historial.cierreSeguimiento(), TipoPaso.SEGUIMIENTO_CERRADO, null);
		pasos.sort(Comparator.comparing(PasoAlerta::fechaHora));
		return List.copyOf(pasos);
	}

	/** Lo que informó el reloj sobre la alerta misma. */
	private static void agregarDelReloj(List<PasoAlerta> pasos, HistorialAlerta historial) {
		agregarSimple(pasos, historial.alerta().recepcion(), TipoPaso.ALERTA_RECIBIDA, null);
		agregarSimple(pasos, historial.agravamiento(), TipoPaso.ALERTA_AGRAVADA, null);
		agregarSimple(pasos, historial.cancelacion(), TipoPaso.ALERTA_CANCELADA, null);
		agregarSimple(pasos, historial.sinRespuesta(), TipoPaso.SIN_RESPUESTA, null);
	}

	/**
	 * Cada aviso con sus estados. Hay salto cuando un aviso a la lista del paciente va a otra persona que
	 * el aviso anterior a esa lista: el correo de respaldo al mismo contacto y los avisos al equipo no lo son.
	 */
	private static void agregarAvisos(List<PasoAlerta> pasos, List<IntentoRegistrado> intentos) {
		UUID anterior = null;
		for (IntentoRegistrado intento : intentos) {
			Instant inicio = intento.envio() != null ? intento.envio() : intento.fallo();
			if (inicio == null) {
				continue;
			}
			if (intento.vinculo() != null) {
				if (anterior != null && !anterior.equals(intento.vinculo())) {
					pasos.add(deAviso(inicio, TipoPaso.SALTO_CONTACTO, intento, null));
				}
				anterior = intento.vinculo();
			}
			agregarEstados(pasos, intento);
		}
	}

	/** Los estados de entrega de un aviso, los que hayan ocurrido. */
	private static void agregarEstados(List<PasoAlerta> pasos, IntentoRegistrado intento) {
		agregarDeAviso(pasos, intento.envio(), TipoPaso.AVISO_ENVIADO, intento, null);
		agregarDeAviso(pasos, intento.entrega(), TipoPaso.AVISO_ENTREGADO, intento, null);
		agregarDeAviso(pasos, intento.lectura(), TipoPaso.AVISO_LEIDO, intento, null);
		agregarDeAviso(pasos, intento.fallo(), TipoPaso.AVISO_FALLIDO, intento, intento.motivoFallo());
		agregarDeAviso(pasos, intento.fechaRespuesta(), TipoPaso.RESPUESTA_CONTACTO, intento, intento.respuesta());
	}

	/**
	 * Lo que hizo el equipo: tomar el caso, sus notas y el cierre. Quien resuelve una alerta sin
	 * responsable queda a cargo a la misma hora del cierre: nadie la asumió antes, así que ese paso no se muestra.
	 */
	private static void agregarAtencion(List<PasoAlerta> pasos, HistorialAlerta historial) {
		if (historial.toma() != null && !historial.toma().equals(historial.cierre())) {
			pasos.add(new PasoAlerta(historial.toma(), TipoPaso.CASO_ASUMIDO, null, null, null, null,
					historial.responsable()));
		}
		for (AccionSeguimiento accion : historial.seguimientos()) {
			pasos.add(new PasoAlerta(accion.fechaHora(), TipoPaso.SEGUIMIENTO, null, null, null, detalle(accion),
					accion.usuario()));
		}
		if (historial.cierre() != null) {
			String clasificacion = historial.clasificacion() == null ? null : historial.clasificacion().name();
			pasos.add(new PasoAlerta(historial.cierre(), TipoPaso.ALERTA_RESUELTA, null, null, null, clasificacion,
					historial.resolutor()));
		}
	}

	/** @return "TIPO a DESTINO: nota", con lo que tenga la acción */
	private static String detalle(AccionSeguimiento accion) {
		String titulo = accion.destino() == null ? accion.tipo() : accion.tipo() + " a " + accion.destino();
		return accion.nota() == null ? titulo : titulo + ": " + accion.nota();
	}

	/** Un aviso al reloj aún sin enviar se muestra a la hora del cierre, que es cuando se registró. */
	private static void agregarAvisosReloj(List<PasoAlerta> pasos, HistorialAlerta historial,
			PoliticaReintentos politica, Instant ahora) {
		for (AvisoRelojRegistrado aviso : historial.avisosReloj()) {
			if (aviso.envio() == null) {
				Instant registro = historial.cierre() != null ? historial.cierre() : historial.alerta().recepcion();
				pasos.add(PasoAlerta.simple(registro, TipoPaso.FCM_PENDIENTE, aviso.tipo()));
			}
			agregarSimple(pasos, aviso.envio(), TipoPaso.FCM_ENVIADO, aviso.tipo());
			agregarSimple(pasos, aviso.recepcion(), TipoPaso.FCM_RECIBIDO, aviso.tipo());
			agregarSimple(pasos, plazoAgotado(aviso, politica, ahora), TipoPaso.FCM_NO_CONFIRMADO, aviso.tipo());
		}
	}

	/**
	 * Tras el último reintento se le da al reloj una espera más para acusarlo, igual a la última.
	 *
	 * @return cuándo venció el plazo; null si el aviso se acusó, aún quedan reintentos o aún puede acusarse
	 */
	private static Instant plazoAgotado(AvisoRelojRegistrado aviso, PoliticaReintentos politica, Instant ahora) {
		if (aviso.envio() == null || aviso.recepcion() != null
				|| politica.esperaTras(aviso.intentos() - 1).isPresent()) {
			return null;
		}
		Instant vencimiento = aviso.envio().plus(politica.esperas().getLast());
		return ahora.isBefore(vencimiento) ? null : vencimiento;
	}

	/** Agrega el paso si ocurrió. */
	private static void agregarSimple(List<PasoAlerta> pasos, Instant fecha, TipoPaso tipo, String detalle) {
		if (fecha != null) {
			pasos.add(PasoAlerta.simple(fecha, tipo, detalle));
		}
	}

	/** Agrega el paso de un aviso si ocurrió. */
	private static void agregarDeAviso(List<PasoAlerta> pasos, Instant fecha, TipoPaso tipo,
			IntentoRegistrado intento, String detalle) {
		if (fecha != null) {
			pasos.add(deAviso(fecha, tipo, intento, detalle));
		}
	}

	/** @return el paso con el contacto, el canal y la ronda de ese aviso */
	private static PasoAlerta deAviso(Instant fecha, TipoPaso tipo, IntentoRegistrado intento, String detalle) {
		return new PasoAlerta(fecha, tipo, intento.contacto(), intento.canal(), intento.ronda(), detalle, null);
	}

}
