package cl.ucn.alertacuida.application.telemetria.modelo;

import cl.ucn.alertacuida.domain.telemetria.TipoConexion;

/**
 * Cómo estaba el reloj al armar el lote.
 *
 * @param minutosPuesto         opcional: minutos puesto desde el lote anterior
 * @param versionConfiguracion  opcional: la que está aplicando (null si aún no recibió ninguna)
 */
public record EstadoInformado(int nivelBateria, boolean relojPuesto, Integer minutosPuesto, TipoConexion conexion,
		String versionApp, String versionConfiguracion) {
}
