package cl.ucn.alertacuida.application.sesion.modelo;

import java.util.UUID;

/**
 * Lo que el inicio de sesión necesita de un usuario. Sin contraseña = cuenta pendiente de activar.
 *
 * @param nombre         nombre del profesional, para mostrar en el dashboard
 * @param codigoRol      ADMINISTRADOR, COORDINADOR o PERSONAL
 * @param hashContrasena hash bcrypt, o vacío si la cuenta no se ha activado
 * @param activo         false si la cuenta o su rol están desactivados
 */
public record CuentaUsuario(UUID id, String nombre, String codigoRol, String hashContrasena, boolean activo) {
}
