package cl.ucn.alertacuida.application.sesion;

import java.util.UUID;

/** Lo que el inicio de sesión necesita de un usuario. Sin contraseña = cuenta pendiente de activar. */
public record CuentaUsuario(UUID id, String nombre, String codigoRol, String hashContrasena, boolean activo) {
}
