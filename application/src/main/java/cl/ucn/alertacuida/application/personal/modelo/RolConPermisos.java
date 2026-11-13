package cl.ucn.alertacuida.application.personal.modelo;

import java.util.Set;

/**
 * Un rol activo con los permisos que tiene hoy.
 *
 * @param esSistema el administrador: sus permisos no se editan
 * @param permisos  "MODULO:ACCION", como en permiso_rol
 */
public record RolConPermisos(String codigo, String nombre, boolean esSistema, Set<String> permisos) {
}
