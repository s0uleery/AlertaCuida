// Endpoints públicos de la respuesta de un contacto desde su correo. El token va en el cuerpo, nunca en la
// dirección, para que no quede en los registros del servidor.
import { pedir } from '../../api/cliente';
import type { RespuestaContacto, VistaRespuesta } from './tipos';

export const respuestaApi = {
  /** La alerta del enlace y lo que ya respondió. No registra nada. */
  consultar: (token: string) =>
    pedir<VistaRespuesta>('/respuestas-contacto/consulta', { method: 'POST', body: JSON.stringify({ token }) }),

  /** Registra la respuesta; si ya había una o la alerta se cerró, devuelve cómo quedó sin cambiarla. */
  responder: (token: string, respuesta: RespuestaContacto) =>
    pedir<VistaRespuesta>('/respuestas-contacto', { method: 'POST', body: JSON.stringify({ token, respuesta }) }),
};
