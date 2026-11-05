package cl.ucn.alertacuida.application.sesion.modelo;

import java.time.Instant;

/**
 * Token de acceso de vida corta.
 *
 * @param valor    el token firmado (JWT), que va en la cabecera Authorization
 * @param expiraEn desde cuándo deja de servir y hay que renovarlo
 */
public record TokenAcceso(String valor, Instant expiraEn) {
}
