import { pedir } from '../../api/cliente';
import type { RespuestaEnlace, SolicitudCuenta } from './tipos';

/** Llamadas al backend para las cuentas del personal (1.3.2). */
export const personalApi = {
  /** Crea la cuenta (exige ADMINISTRACION:CREAR) y devuelve su enlace de activación. */
  crear: (solicitud: SolicitudCuenta) =>
    pedir<RespuestaEnlace>('/personal', { method: 'POST', body: JSON.stringify(solicitud) }),

  /** Emite otro enlace para una cuenta pendiente; el anterior deja de servir. */
  nuevoEnlace: (idUsuario: string) =>
    pedir<RespuestaEnlace>(`/personal/${idUsuario}/enlace-activacion`, { method: 'POST' }),

  /** Guarda la contraseña elegida con el token del enlace. Es pública: no necesita sesión. */
  activar: (token: string, contrasena: string) =>
    pedir<void>('/auth/activar', { method: 'POST', body: JSON.stringify({ token, contrasena }) }),
};
