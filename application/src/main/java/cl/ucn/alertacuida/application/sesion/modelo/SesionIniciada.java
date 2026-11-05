package cl.ucn.alertacuida.application.sesion.modelo;

/**
 * Resultado de un inicio de sesión o de una renovación correctos.
 *
 * @param token      lo que el dashboard manda en cada petición
 * @param usuario    quién inició sesión y qué puede hacer
 * @param renovacion permite pedir otro token de acceso cuando este venza
 */
public record SesionIniciada(TokenAcceso token, UsuarioSesion usuario, TokenRenovacion renovacion) {
}
