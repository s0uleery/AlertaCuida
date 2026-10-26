package cl.ucn.alertacuida.application.sesion.modelo;

/**
 * Resultado de un inicio de sesión correcto.
 *
 * @param token   lo que el dashboard manda en cada petición
 * @param usuario quién inició sesión y qué puede hacer
 */
public record SesionIniciada(TokenAcceso token, UsuarioSesion usuario) {
}
