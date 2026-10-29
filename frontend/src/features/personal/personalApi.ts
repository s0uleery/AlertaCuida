import { pedir } from '../../api/cliente';
import type { Permiso } from '../sesion/tipos';
import type { PerfilEquipo, RespuestaEnlace, Rol, RolConPermisos, SolicitudCuenta } from './tipos';

// PENDIENTE EN EL BACKEND: hoy solo existen POST /api/personal, POST /api/personal/{id}/enlace-activacion
// y POST /api/auth/activar. Faltan los endpoints de abajo marcados con "Falta en el backend"; mientras no
// existan, las pestañas Perfiles y Permisos muestran el error del servidor (no hay datos de ejemplo).
// En Permisos.java faltan también ADMINISTRACION_VER y ADMINISTRACION_EDITAR.

/** Llamadas al backend para las cuentas del personal y los permisos de los roles. */
export const personalApi = {
  /**
   * Cuentas del equipo, por nombre.
   *
   * Falta en el backend: GET /api/personal con @PreAuthorize(ADMINISTRACION:VER). Devuelve PerfilEquipo[]:
   * el estado sale de usuario.activo y hash_contrasena (ver V1) y ultimoAcceso del último LOGIN en
   * registro_auditoria (índice registro_auditoria_usuario). El RUN no se devuelve: va cifrado.
   */
  listar: () => pedir<PerfilEquipo[]>('/personal'),

  /** Crea la cuenta (exige ADMINISTRACION:CREAR) y devuelve su enlace de activación. */
  crear: (solicitud: SolicitudCuenta) =>
    pedir<RespuestaEnlace>('/personal', { method: 'POST', body: JSON.stringify(solicitud) }),

  /**
   * Cambia el rol de una cuenta.
   *
   * Falta en el backend: PUT /api/personal/{id}/rol con cuerpo { rol }, @PreAuthorize(ADMINISTRACION:EDITAR),
   * 204 si resulta. Debe rechazar (409) que alguien cambie su propio rol o que quede sin ningún
   * administrador activo, y auditar MODIFICAR USUARIO con detalle { rolAnterior, rolNuevo }.
   */
  cambiarRol: (id: string, rol: Rol) =>
    pedir<void>(`/personal/${id}/rol`, { method: 'PUT', body: JSON.stringify({ rol }) }),

  /** Emite otro enlace para una cuenta pendiente; el anterior deja de servir. */
  nuevoEnlace: (idUsuario: string) =>
    pedir<RespuestaEnlace>(`/personal/${idUsuario}/enlace-activacion`, { method: 'POST' }),

  /** Guarda la contraseña elegida con el token del enlace. Es pública: no necesita sesión. */
  activar: (token: string, contrasena: string) =>
    pedir<void>('/auth/activar', { method: 'POST', body: JSON.stringify({ token, contrasena }) }),

  /**
   * Roles activos con sus permisos, el administrador primero.
   *
   * Falta en el backend: GET /api/roles con @PreAuthorize(ADMINISTRACION:VER). Devuelve RolConPermisos[]
   * desde las tablas rol (activo = true) y permiso_rol.
   */
  roles: () => pedir<RolConPermisos[]>('/roles'),

  /**
   * Reemplaza todos los permisos de un rol.
   *
   * Falta en el backend: PUT /api/roles/{codigo}/permisos con cuerpo { permisos: ["ALERTAS:VER", …] },
   * @PreAuthorize(ADMINISTRACION:EDITAR), 204 si resulta. Debe rechazar (409) un rol con es_sistema
   * (el administrador) y auditar MODIFICAR PERMISO_ROL con detalle { agregados, quitados }.
   */
  guardarPermisos: (codigo: Rol, permisos: Permiso[]) =>
    pedir<void>(`/roles/${codigo}/permisos`, { method: 'PUT', body: JSON.stringify({ permisos }) }),
};
