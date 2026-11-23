package cl.ucn.alertacuida.application.panel.modelo;

/**
 * Los números de arriba de la portada.
 *
 * @param relojesRojo     relojes con semáforo rojo (sin señal o plan vencido)
 * @param relojesAmarillo relojes con semáforo amarillo
 */
public record TotalesPanel(int alertasAbiertas, int sinRespuesta, int enSeguimiento, int relojesRojo,
		int relojesAmarillo) {
}
