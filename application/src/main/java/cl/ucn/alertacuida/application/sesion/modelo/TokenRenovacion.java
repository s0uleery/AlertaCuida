package cl.ucn.alertacuida.application.sesion.modelo;

import java.time.Instant;

/**
 * Token de renovación recién emitido. Es lo único que permite pedir otro token de acceso sin volver
 * a escribir la contraseña; viaja en una cookie que el código del navegador no puede leer.
 *
 * @param valor    el secreto; en la base solo queda su hash
 * @param expiraEn límite de la sesión: desde ahí hay que volver a iniciar sesión
 */
public record TokenRenovacion(String valor, Instant expiraEn) {
}
