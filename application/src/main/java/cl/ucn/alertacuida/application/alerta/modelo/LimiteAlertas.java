package cl.ucn.alertacuida.application.alerta.modelo;

import java.time.Duration;

/**
 * Cuántas alertas puede enviar un reloj en una ventana de tiempo antes de considerarlo con una
 * posible falla (alertacuida.alertas.* en application.properties). Pasar el límite no rechaza
 * ninguna alerta: crea una alerta técnica EXCESO_ALERTAS para que el equipo revise el reloj.
 *
 * @param avisarDesde con esta cantidad de alertas en la ventana (contando la que llega) se avisa
 * @param ventana tiempo hacia atrás en que se cuentan
 */
public record LimiteAlertas(int avisarDesde, Duration ventana) {
}
