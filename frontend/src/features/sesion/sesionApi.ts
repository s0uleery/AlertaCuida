import { pedir } from '../../api/cliente';
import type { RespuestaLogin, Usuario } from './tipos';

/** Llamadas al backend para iniciar sesión y saber quién la tiene. */
export const sesionApi = {
  iniciar: (run: string, contrasena: string) =>
    pedir<RespuestaLogin>('/auth/login', { method: 'POST', body: JSON.stringify({ run, contrasena }) }),

  usuarioActual: () => pedir<Usuario>('/auth/yo'),
};
