import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';

/** Muestra un aviso breve abajo a la derecha. */
type Avisar = (mensaje: string) => void;

const ToastContext = createContext<Avisar | null>(null);

/** Cuánto se muestra cada aviso. */
const DURACION_MS = 3200;

/**
 * Avisos breves del mockup ("toast"): fondo navy, borde turquesa y ✓. Confirman que una acción se guardó sin
 * ocupar espacio en la pantalla. Se anuncian a los lectores de pantalla (role=status).
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [mensaje, setMensaje] = useState<string | null>(null);
  const temporizador = useRef<number | undefined>(undefined);

  const avisar = useCallback<Avisar>((texto) => {
    window.clearTimeout(temporizador.current);
    setMensaje(texto);
    temporizador.current = window.setTimeout(() => setMensaje(null), DURACION_MS);
  }, []);

  useEffect(() => () => window.clearTimeout(temporizador.current), []);

  return (
    <ToastContext.Provider value={avisar}>
      {children}
      <div role="status" aria-live="polite" className="pointer-events-none fixed right-4 bottom-4 z-50 max-w-sm">
        {mensaje && (
          <div className="rounded-panel border-l-4 border-teal bg-navy px-4 py-3 text-[13px] text-white shadow-toast">
            <span aria-hidden="true" className="mr-2 font-bold text-teal">
              ✓
            </span>
            {mensaje}
          </div>
        )}
      </div>
    </ToastContext.Provider>
  );
}

/** @returns la función para mostrar un aviso breve */
export function useToast(): Avisar {
  const avisar = useContext(ToastContext);
  if (!avisar) throw new Error('useToast necesita ToastProvider');
  return avisar;
}
