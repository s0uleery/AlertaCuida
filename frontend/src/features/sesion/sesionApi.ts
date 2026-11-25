import { pedir } from '../../api/cliente';
import type { RespuestaLogin } from './tipos';

/** Llamadas al backend para iniciar, renovar y cerrar la sesión. La cookie la maneja el navegador. */
export const sesionApi = {
  iniciar: (run: string, contrasena: string) =>
    pedir<RespuestaLogin>('/auth/login', { method: 'POST', body: JSON.stringify({ run, contrasena }) }),

  renovar: () => pedir<RespuestaLogin>('/auth/renovar', { method: 'POST' }),

  salir: () => pedir<void>('/auth/salir', { method: 'POST' }),

  /** Pide el correo de recuperación. Responde lo mismo exista o no la cuenta. */
  recuperar: (run: string) => pedir<void>('/auth/recuperar', { method: 'POST', body: JSON.stringify({ run }) }),

  /** Elige la contraseña nueva con el token del correo de recuperación. */
  restablecer: (token: string, contrasena: string) =>
    pedir<void>('/auth/restablecer', { method: 'POST', body: JSON.stringify({ token, contrasena }) }),
};
