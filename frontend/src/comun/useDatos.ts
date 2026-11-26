import { useCallback, useEffect, useRef, useState } from 'react';
import { ErrorApi } from '../api/cliente';

/** Estado de una carga desde el backend. */
export interface Datos<T> {
  /** Lo cargado, o null mientras carga o si falló */
  datos: T | null;
  /** Mensaje para mostrar si falló */
  error: string | null;
  cargando: boolean;
  /** Vuelve a pedir los datos (por ejemplo, después de guardar un cambio) */
  recargar: () => void;
}

/** Opciones de {@link useDatos}. */
export interface OpcionesDatos {
  /**
   * Cada cuántos milisegundos vuelve a pedir los datos en silencio, para ver lo nuevo sin refrescar la página
   * (por ejemplo, los avisos y el seguimiento de una alerta). Sin este valor, solo carga al mostrarse.
   */
  cadaMs?: number;
}

/**
 * Carga datos del backend al mostrar el componente y cada vez que cambia `clave`.
 * Si el componente desaparece antes de que llegue la respuesta, la ignora.
 *
 * Con `cadaMs`, además vuelve a pedirlos cada tanto, sin mostrar "cargando". Si una de esas consultas falla,
 * conserva lo último que tenía y no muestra el error: suele ser un corte pasajero. Mientras la pestaña está
 * oculta no consulta, y al volver a ella consulta de inmediato.
 *
 * @param cargar   función que pide los datos (ej. () => pacientesApi.listar(false))
 * @param clave    texto que, al cambiar, obliga a cargar de nuevo (ej. el id de la página)
 * @param opciones ver {@link OpcionesDatos}
 */
export function useDatos<T>(cargar: () => Promise<T>, clave = '', opciones: OpcionesDatos = {}): Datos<T> {
  const [datos, setDatos] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);
  const [version, setVersion] = useState(0);
  // Si ya hay datos, una carga nueva no vuelve a mostrar "cargando" ni borra lo que había por un error
  const tieneDatos = useRef(false);
  const silenciosa = useRef(false);

  // cargar cambia en cada render; se usa la clave para decidir cuándo volver a pedir
  const pedir = useCallback(cargar, [clave]);

  useEffect(() => {
    tieneDatos.current = false;
  }, [clave]);

  useEffect(() => {
    let vigente = true;
    const enSilencio = silenciosa.current && tieneDatos.current;
    silenciosa.current = false;
    if (!enSilencio) setCargando(true);
    pedir()
      .then((resultado) => {
        if (vigente) {
          tieneDatos.current = true;
          setDatos(resultado);
          setError(null);
        }
      })
      .catch((e) => {
        if (vigente && !enSilencio) {
          setError(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.');
        }
      })
      .finally(() => {
        if (vigente && !enSilencio) setCargando(false);
      });
    return () => {
      vigente = false;
    };
  }, [pedir, version]);

  const recargar = useCallback(() => setVersion((v) => v + 1), []);

  const { cadaMs } = opciones;
  useEffect(() => {
    if (!cadaMs) return;
    /** Vuelve a pedir sin mostrar "cargando", solo con la pestaña a la vista. */
    const actualizar = () => {
      if (document.visibilityState !== 'visible') return;
      silenciosa.current = true;
      setVersion((v) => v + 1);
    };
    const intervalo = window.setInterval(actualizar, cadaMs);
    document.addEventListener('visibilitychange', actualizar);
    return () => {
      window.clearInterval(intervalo);
      document.removeEventListener('visibilitychange', actualizar);
    };
  }, [cadaMs]);

  return { datos, error, cargando, recargar };
}
