import { ContrasenaConEnlace } from '../personal/ContrasenaConEnlace';
import { sesionApi } from './sesionApi';

/**
 * Página pública /restablecer#<token>, que abre la persona desde el correo de recuperación para elegir una
 * contraseña nueva. El enlace sirve una vez y vence en una hora.
 */
export function PaginaRestablecer() {
  return (
    <ContrasenaConEnlace
      textos={{
        subtitulo: 'Elige una contraseña nueva',
        enlaceIncompleto:
          'El enlace está incompleto. Ábrelo tal como te llegó o vuelve a pedirlo desde la página de ingreso.',
        boton: 'Guardar contraseña',
        enviando: 'Guardando…',
        exito: 'Listo: tu contraseña cambió. Ya puedes ingresar con tu RUN y la contraseña nueva.',
      }}
      guardar={sesionApi.restablecer}
    />
  );
}
