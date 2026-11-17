import { pedir } from '../../api/cliente';
import type { Permiso } from '../sesion/tipos';
import type { PerfilEquipo, RespuestaEnlace, Rol, RolConPermisos, SolicitudCambioRol, SolicitudCuenta } from './tipos';

/** Llamadas al backend para las cuentas del personal y los permisos de los roles. */
export const personalApi = {
  /** Cuentas del equipo, por nombre (exige ADMINISTRACION:VER). No trae el RUN ni el teléfono: van cifrados. */
  listar: () => pedir<PerfilEquipo[]>('/personal'),

  /** Crea la cuenta (exige ADMINISTRACION:CREAR) y devuelve su enlace de activación. */
  crear: (solicitud: SolicitudCuenta) =>
    pedir<RespuestaEnlace>('/personal', { method: 'POST', body: JSON.stringify(solicitud) }),

  /**
   * Cambia el rol de otra cuenta (exige ADMINISTRACION:EDITAR). Responde 409 si es la propia o si dejaría
   * el sistema sin administrador activo, y 400 si pasa a COORDINADOR sin teléfono.
   */
  cambiarRol: (id: string, solicitud: SolicitudCambioRol) =>
    pedir<void>(`/personal/${id}/rol`, { method: 'PUT', body: JSON.stringify(solicitud) }),

  /** Enciende o apaga el correo de las alertas técnicas de una cuenta (exige ADMINISTRACION:EDITAR). */
  cambiarRecibeAvisos: (id: string, recibeAvisos: boolean) =>
    pedir<void>(`/personal/${id}/recibe-avisos`, { method: 'PUT', body: JSON.stringify({ recibeAvisos }) }),

  /** Emite otro enlace para una cuenta pendiente; el anterior deja de servir. */
  nuevoEnlace: (idUsuario: string) =>
    pedir<RespuestaEnlace>(`/personal/${idUsuario}/enlace-activacion`, { method: 'POST' }),

  /** Guarda la contraseña elegida con el token del enlace. Es pública: no necesita sesión. */
  activar: (token: string, contrasena: string) =>
    pedir<void>('/auth/activar', { method: 'POST', body: JSON.stringify({ token, contrasena }) }),

  /** Roles activos con sus permisos, el administrador primero (exige ADMINISTRACION:VER). */
  roles: () => pedir<RolConPermisos[]>('/roles'),

  /**
   * Reemplaza todos los permisos de un rol (exige ADMINISTRACION:EDITAR). Responde 409 con el
   * administrador, cuyos permisos no se cambian.
   */
  guardarPermisos: (codigo: Rol, permisos: Permiso[]) =>
    pedir<void>(`/roles/${codigo}/permisos`, { method: 'PUT', body: JSON.stringify({ permisos }) }),
};
