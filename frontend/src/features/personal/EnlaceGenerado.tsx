import { useState } from 'react';
import { ErrorApi } from '../../api/cliente';
import { CLASE_BOTON_PRINCIPAL, CLASE_BOTON_SECUNDARIO, CLASE_CAMPO, CLASE_ERROR } from '../../comun/estilos';
import { formatearFechaHora } from '../../comun/fechas';
import { personalApi } from './personalApi';
import type { RespuestaEnlace } from './tipos';

/** Propiedades de EnlaceGenerado. */
interface Props {
  /** Enlace recién creado */
  enlace: RespuestaEnlace;
  /** Nombre de la persona, para el mensaje */
  nombre: string;
  /** Vuelve al formulario vacío */
  alTerminar: () => void;
}

/**
 * Resultado del alta. Lo normal es que el enlace de activación le llegue por correo a la persona: entonces no
 * se muestra aquí, así solo ella lo conoce. Si el correo no salió (falló, o está en modo simulado en
 * desarrollo), se muestra para que el administrador lo copie y se lo pase. El enlace no se puede volver a ver,
 * porque el backend solo guarda su hash: si se pierde, se envía otro.
 */
export function EnlaceGenerado({ enlace: inicial, nombre, alTerminar }: Props) {
  const [enlace, setEnlace] = useState(inicial);
  const [copiado, setCopiado] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [generando, setGenerando] = useState(false);
  const [reenviado, setReenviado] = useState(false);

  /** Copia el enlace al portapapeles. */
  async function copiar() {
    if (!enlace.enlaceActivacion) return;
    try {
      await navigator.clipboard.writeText(enlace.enlaceActivacion);
      setCopiado(true);
    } catch {
      // Sin permiso para el portapapeles: el enlace queda seleccionado para copiarlo a mano
      setError('No se pudo copiar automáticamente. Selecciona el enlace y cópialo.');
    }
  }

  /** Pide otro enlace al backend, que lo vuelve a enviar por correo; el anterior deja de servir. */
  async function generarOtro() {
    setGenerando(true);
    setError(null);
    try {
      setEnlace(await personalApi.nuevoEnlace(enlace.idUsuario));
      setCopiado(false);
      setReenviado(true);
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.');
    } finally {
      setGenerando(false);
    }
  }

  return (
    <div className="space-y-4">
      {enlace.correoEnviado ? (
        <div role="status" className="rounded-md bg-verde-tinte px-4 py-3 text-verde-texto">
          <p className="font-medium">Cuenta creada para {nombre}.</p>
          <p className="text-sm">
            {reenviado ? 'Le enviamos un enlace nuevo' : 'Le enviamos un correo'} para que elija su contraseña. El
            enlace sirve una sola vez y vence el <strong>{formatearFechaHora(enlace.expiraEn)}</strong>.
          </p>
          <p className="mt-1 text-sm">
            Si no le llega (revisa también la carpeta de spam) o vence, envía otro: el anterior deja de servir.
          </p>
        </div>
      ) : (
        <>
          <div role="alert" className="rounded-md bg-naranja-tinte px-4 py-3 text-naranja-texto">
            <p className="font-medium">Cuenta creada para {nombre}, pero no se pudo enviar el correo.</p>
            <p className="text-sm">
              Pásale este enlace por otra vía para que elija su contraseña. Sirve una sola vez y vence el{' '}
              <strong>{formatearFechaHora(enlace.expiraEn)}</strong>.
            </p>
          </div>

          <input
            readOnly
            value={enlace.enlaceActivacion ?? ''}
            onFocus={(e) => e.target.select()}
            aria-label="Enlace de activación"
            className={`${CLASE_CAMPO} font-mono text-sm`}
          />

          <p className="text-sm text-apagado">
            Este enlace no se vuelve a mostrar. Si se pierde o vence, genera otro: el anterior deja de servir.
          </p>
        </>
      )}

      {error && (
        <p role="alert" className={CLASE_ERROR}>
          {error}
        </p>
      )}

      <div className="flex flex-wrap gap-2">
        {!enlace.correoEnviado && (
          <button onClick={copiar} className={CLASE_BOTON_PRINCIPAL}>
            {copiado ? 'Copiado ✓' : 'Copiar enlace'}
          </button>
        )}
        <button onClick={generarOtro} disabled={generando} className={CLASE_BOTON_SECUNDARIO}>
          {generando ? 'Enviando…' : enlace.correoEnviado ? 'Enviar otro enlace' : 'Intentar enviar de nuevo'}
        </button>
        <button onClick={alTerminar} className={CLASE_BOTON_SECUNDARIO}>
          Crear otra cuenta
        </button>
      </div>
    </div>
  );
}
