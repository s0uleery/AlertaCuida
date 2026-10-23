import { useState } from 'react';
import { ErrorApi } from '../../api/cliente';
import { CLASE_BOTON_PRINCIPAL, CLASE_BOTON_SECUNDARIO, CLASE_CAMPO, CLASE_ERROR } from '../../comun/estilos';
import { formatearFechaHora } from '../../comun/fechas';
import { personalApi } from './personalApi';
import type { RespuestaEnlace } from './tipos';

interface Props {
  /** Enlace recién creado */
  enlace: RespuestaEnlace;
  /** Nombre de la persona, para el mensaje */
  nombre: string;
  /** Vuelve al formulario vacío */
  alTerminar: () => void;
}

/**
 * Muestra el enlace de activación para que el administrador lo copie y se lo pase a la persona.
 * Mientras no exista el correo (2.4) es la única forma de entregarlo: el enlace no se puede volver
 * a ver, porque el backend solo guarda su hash. Si se pierde, se genera otro.
 */
export function EnlaceGenerado({ enlace: inicial, nombre, alTerminar }: Props) {
  const [enlace, setEnlace] = useState(inicial);
  const [copiado, setCopiado] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [generando, setGenerando] = useState(false);

  async function copiar() {
    try {
      await navigator.clipboard.writeText(enlace.enlaceActivacion);
      setCopiado(true);
    } catch {
      // Sin permiso para el portapapeles: el enlace queda seleccionado para copiarlo a mano
      setError('No se pudo copiar automáticamente. Selecciona el enlace y cópialo.');
    }
  }

  async function generarOtro() {
    setGenerando(true);
    setError(null);
    try {
      setEnlace(await personalApi.nuevoEnlace(enlace.idUsuario));
      setCopiado(false);
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.');
    } finally {
      setGenerando(false);
    }
  }

  return (
    <div className="space-y-4">
      <div className="rounded-md bg-emerald-50 px-4 py-3 text-emerald-900">
        <p className="font-medium">Cuenta creada para {nombre}.</p>
        <p className="text-sm">
          Envíale este enlace para que elija su contraseña. Sirve una sola vez y vence el{' '}
          <strong>{formatearFechaHora(enlace.expiraEn)}</strong>.
        </p>
      </div>

      <input
        readOnly
        value={enlace.enlaceActivacion}
        onFocus={(e) => e.target.select()}
        aria-label="Enlace de activación"
        className={`${CLASE_CAMPO} font-mono text-sm`}
      />

      <p className="text-sm text-slate-500">
        Este enlace no se vuelve a mostrar. Si se pierde o vence, genera otro: el anterior deja de servir.
      </p>

      {error && (
        <p role="alert" className={CLASE_ERROR}>
          {error}
        </p>
      )}

      <div className="flex flex-wrap gap-2">
        <button onClick={copiar} className={CLASE_BOTON_PRINCIPAL}>
          {copiado ? 'Copiado ✓' : 'Copiar enlace'}
        </button>
        <button onClick={generarOtro} disabled={generando} className={CLASE_BOTON_SECUNDARIO}>
          {generando ? 'Generando…' : 'Generar otro enlace'}
        </button>
        <button onClick={alTerminar} className={CLASE_BOTON_SECUNDARIO}>
          Crear otra cuenta
        </button>
      </div>
    </div>
  );
}
