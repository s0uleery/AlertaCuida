package cl.ucn.alertacuida.application.panel.modelo;

import java.util.List;

/**
 * El panel general de la portada.
 *
 * @param usuarios los activos: primero los que tienen más alertas abiertas, después el semáforo más
 *                 preocupante y por último el apellido
 */
public record Panel(TotalesPanel totales, List<UsuarioPanel> usuarios) {
}
