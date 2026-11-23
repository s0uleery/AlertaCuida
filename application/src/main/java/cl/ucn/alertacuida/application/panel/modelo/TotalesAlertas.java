package cl.ucn.alertacuida.application.panel.modelo;

/**
 * Totales de alertas de la portada. Abierta = igual que la lista: sin cerrar por el equipo (en seguimiento
 * cuenta), sin canceladas ni simulacros.
 *
 * @param alertasAbiertas de salud y técnicas
 * @param sinRespuesta    abiertas en las que el reloj terminó la lista sin confirmación
 * @param enSeguimiento   positivas con el seguimiento sin cerrar
 */
public record TotalesAlertas(int alertasAbiertas, int sinRespuesta, int enSeguimiento) {
}
