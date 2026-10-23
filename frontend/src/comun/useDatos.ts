import { useCallback, useEffect, useState } from 'react';
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

/**
 * Carga datos del backend al mostrar el componente y cada vez que cambia `clave`.
 * Si el componente desaparece antes de que llegue la respuesta, la ignora.
 *
 * @param cargar función que pide los datos (ej. () => pacientesApi.listar(false))
 * @param clave  texto que, al cambiar, obliga a cargar de nuevo (ej. el id de la página)
 */
export function useDatos<T>(cargar: () => Promise<T>, clave = ''): Datos<T> {
  const [datos, setDatos] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);
  const [version, setVersion] = useState(0);

  // cargar cambia en cada render; se usa la clave para decidir cuándo volver a pedir
  const pedir = useCallback(cargar, [clave]);

  useEffect(() => {
    let vigente = true;
    setCargando(true);
    pedir()
      .then((resultado) => {
        if (vigente) {
          setDatos(resultado);
          setError(null);
        }
      })
      .catch((e) => {
        if (vigente) setError(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.');
      })
      .finally(() => {
        if (vigente) setCargando(false);
      });
    return () => {
      vigente = false;
    };
  }, [pedir, version]);

  const recargar = useCallback(() => setVersion((v) => v + 1), []);
  return { datos, error, cargando, recargar };
}
