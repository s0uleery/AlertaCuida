import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { CLASE_BOTON_SECUNDARIO } from './estilos';
import { claseBoton } from './ui/Boton';

/** Lo que se muestra en el diálogo de confirmación. */
export interface OpcionesConfirmacion {
  titulo: string;
  /** Explicación bajo el título, por ejemplo qué se conserva */
  mensaje?: string;
  /** Texto del botón que confirma; por defecto "Confirmar" */
  textoConfirmar?: string;
  /** Pinta el botón de rojo, para acciones que quitan o retiran algo */
  peligro?: boolean;
}

/** Abre el diálogo y se resuelve con true si se confirmó, false si se canceló. */
type Confirmar = (opciones: OpcionesConfirmacion) => Promise<boolean>;

const ConfirmacionContext = createContext<Confirmar | null>(null);

/** Diálogo pendiente: sus opciones y cómo avisar la respuesta a quien lo abrió. */
interface Pendiente {
  opciones: OpcionesConfirmacion;
  responder: (confirmado: boolean) => void;
}

/**
 * Reemplaza a window.confirm con un diálogo propio de la plataforma. Va una sola vez cerca de la
 * raíz; los componentes lo usan con useConfirmar().
 */
export function ConfirmacionProvider({ children }: { children: ReactNode }) {
  const [pendiente, setPendiente] = useState<Pendiente | null>(null);

  const confirmar = useCallback<Confirmar>(
    (opciones) => new Promise((resolve) => setPendiente({ opciones, responder: resolve })),
    [],
  );

  /** Cierra el diálogo y entrega la respuesta. */
  function cerrar(confirmado: boolean) {
    pendiente?.responder(confirmado);
    setPendiente(null);
  }

  return (
    <ConfirmacionContext.Provider value={confirmar}>
      {children}
      {pendiente && <DialogoConfirmacion opciones={pendiente.opciones} alResponder={cerrar} />}
    </ConfirmacionContext.Provider>
  );
}

/** Propiedades de DialogoConfirmacion. */
interface PropsDialogo {
  opciones: OpcionesConfirmacion;
  alResponder: (confirmado: boolean) => void;
}

/** El diálogo en sí. Escape o un clic fuera de la tarjeta cuentan como cancelar. */
function DialogoConfirmacion({ opciones, alResponder }: PropsDialogo) {
  const { titulo, mensaje, textoConfirmar = 'Confirmar', peligro = false } = opciones;
  const ref = useRef<HTMLDialogElement>(null);

  // showModal bloquea el resto de la página y lleva el foco al diálogo
  useEffect(() => {
    ref.current?.showModal();
  }, []);

  return (
    <dialog
      ref={ref}
      aria-labelledby="confirmacion-titulo"
      onCancel={(e) => {
        e.preventDefault();
        alResponder(false);
      }}
      // El clic cae en el <dialog> mismo solo cuando es en el fondo, fuera de la tarjeta
      onClick={(e) => {
        if (e.target === e.currentTarget) alResponder(false);
      }}
      className="m-auto w-[calc(100%-2rem)] max-w-md rounded-lg border border-linea-oscura bg-superficie p-0 text-texto shadow-modal backdrop:bg-navy/55"
    >
      <div className="p-5">
        <h2 id="confirmacion-titulo" className="text-[15px] font-bold text-navy">
          {titulo}
        </h2>
        {mensaje && <p className="mt-2 text-[13px] text-apagado">{mensaje}</p>}
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" onClick={() => alResponder(false)} className={CLASE_BOTON_SECUNDARIO} autoFocus>
            Cancelar
          </button>
          <button
            type="button"
            onClick={() => alResponder(true)}
            className={claseBoton(peligro ? 'peligro' : 'principal')}
          >
            {textoConfirmar}
          </button>
        </div>
      </div>
    </dialog>
  );
}

/** @returns la función que abre el diálogo. Solo funciona dentro de <ConfirmacionProvider>. */
export function useConfirmar(): Confirmar {
  const confirmar = useContext(ConfirmacionContext);
  if (!confirmar) {
    throw new Error('useConfirmar debe usarse dentro de <ConfirmacionProvider>');
  }
  return confirmar;
}
