import { useEffect, useId, useRef, type ReactNode } from 'react';

/**
 * Diálogo modal del sistema de diseño, sobre &lt;dialog&gt; nativo: bloquea la página, lleva el foco adentro y se
 * cierra con Escape o con un clic en el fondo (salvo mientras guarda). Se muestra mientras está montado.
 */
export function Dialogo({
  titulo,
  children,
  pie,
  alCerrar,
  ocupado = false,
  ancho = 'max-w-lg',
}: {
  titulo: ReactNode;
  children: ReactNode;
  /** Botones al pie, a la derecha */
  pie?: ReactNode;
  alCerrar: () => void;
  /** Mientras guarda no se puede cerrar */
  ocupado?: boolean;
  /** Clase de ancho máximo de Tailwind */
  ancho?: string;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const idTitulo = useId();

  useEffect(() => {
    const dialogo = ref.current;
    if (dialogo && !dialogo.open) dialogo.showModal();
  }, []);

  return (
    <dialog
      ref={ref}
      aria-labelledby={idTitulo}
      onCancel={(e) => {
        e.preventDefault();
        if (!ocupado) alCerrar();
      }}
      onClick={(e) => {
        if (e.target === ref.current && !ocupado) alCerrar();
      }}
      className={`m-auto w-[calc(100%-2rem)] ${ancho} rounded-lg border border-linea-oscura bg-superficie p-0 text-texto shadow-modal backdrop:bg-navy/55`}
    >
      <div className="flex items-center justify-between gap-3 border-b border-linea px-5 py-3.5">
        <h2 id={idTitulo} className="text-[15px] font-bold text-navy">
          {titulo}
        </h2>
        <button
          type="button"
          onClick={alCerrar}
          disabled={ocupado}
          aria-label="Cerrar"
          className="rounded px-2 py-1 text-apagado hover:bg-superficie-sub disabled:opacity-40"
        >
          ✕
        </button>
      </div>
      <div className="px-5 py-4">{children}</div>
      {pie && <div className="flex flex-wrap justify-end gap-2 border-t border-linea px-5 py-3">{pie}</div>}
    </dialog>
  );
}
