import { ContrasenaConEnlace } from './ContrasenaConEnlace';
import { personalApi } from './personalApi';

/** Página pública /activar#<token>, que abre la persona nueva desde su enlace para elegir su contraseña. */
export function PaginaActivar() {
  return (
    <ContrasenaConEnlace
      textos={{
        subtitulo: 'Activa tu cuenta del PRLAC',
        enlaceIncompleto: 'El enlace está incompleto. Ábrelo tal como te llegó o pide uno nuevo al administrador.',
        boton: 'Activar cuenta',
        enviando: 'Activando…',
        exito: 'Tu cuenta está activa. Ya puedes ingresar con tu RUN y la contraseña que elegiste.',
      }}
      guardar={personalApi.activar}
    />
  );
}
