package cl.ucn.alertacuida.application.respuesta.modelo;

import cl.ucn.alertacuida.domain.alerta.ClasificacionAlerta;
import java.time.Instant;
import java.util.UUID;

/**
 * Lo que ve un coordinador en la página del enlace del correo: en qué va la oficina con la alerta y lo que
 * necesita cada mensaje N1 (decisiones del 26-11-2026). Las fechas son null si ese paso no ocurrió.
 *
 * @param idAlerta            para el enlace a la ficha del dashboard (pide iniciar sesión)
 * @param etapa               qué toca ahora
 * @param responsable         nombre de quien la tiene a cargo; null si nadie
 * @param esResponsable       si es este coordinador: solo él da los pasos
 * @param resultadoLlamada    cómo resultó la llamada
 * @param personalAsignado    cuándo se asignó personal para ir
 * @param sinDisponibilidad   cuándo dijo que no había personal
 * @param llegada             cuándo llegó el personal al domicilio
 * @param resultadoVisita     cómo lo encontró el personal
 * @param recordatorio        si el personal lleva más del plazo sin llegar (N1-08)
 * @param cierre              cuándo se cerró la alerta
 * @param clasificacion       con qué se cerró
 * @param ultimoContacto      nombre del último contacto del paciente al que avisó el reloj (N1-06); null si
 *                            ninguno
 * @param avisoUltimoContacto cuándo se le avisó
 */
public record VistaOficina(UUID idAlerta, EtapaOficina etapa, String responsable, boolean esResponsable,
		ResultadoLlamadaOficina resultadoLlamada, Instant personalAsignado, Instant sinDisponibilidad,
		Instant llegada, ResultadoVisita resultadoVisita, boolean recordatorio, Instant cierre,
		ClasificacionAlerta clasificacion, String ultimoContacto, Instant avisoUltimoContacto) {
}
