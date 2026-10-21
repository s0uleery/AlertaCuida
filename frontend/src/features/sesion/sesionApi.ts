import { pedir } from '../../api/cliente';
import type { RespuestaLogin, Usuario } from './tipos';

export const sesionApi = {
  iniciar: (run: string, contrasena: string) =>
    pedir<RespuestaLogin>('/auth/login', { method: 'POST', body: JSON.stringify({ run, contrasena }) }),

  usuarioActual: () => pedir<Usuario>('/auth/yo'),
};
