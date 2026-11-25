import type { ReactNode } from 'react';

/** Tono de un aviso: cada uno con su color del sistema de diseño. */
export type TonoAviso = 'info' | 'advertencia' | 'error' | 'exito';

const TONO: Record<TonoAviso, string> = {
  info: 'border-teal-borde bg-teal-tinte text-navy',
  advertencia: 'border-naranja-borde bg-naranja-tinte text-naranja-texto',
  error: 'border-magenta-borde bg-magenta-tinte text-magenta',
  exito: 'border-verde-borde bg-verde-tinte text-verde-texto',
};

/**
 * Mensaje destacado. Los errores se anuncian a los lectores de pantalla de inmediato (role=alert); los demás,
 * cuando hay una pausa (role=status).
 */
export function Aviso({
  tono = 'info',
  titulo,
  children,
  className = '',
}: {
  tono?: TonoAviso;
  titulo?: ReactNode;
  children?: ReactNode;
  className?: string;
}) {
  return (
    <div
      role={tono === 'error' ? 'alert' : 'status'}
      className={`rounded-[5px] border px-3.5 py-2.5 text-[13px] ${TONO[tono]} ${className}`}
    >
      {titulo && <p className="font-bold">{titulo}</p>}
      {children}
    </div>
  );
}

/** Banner de acción urgente del mockup: borde magenta a la izquierda, texto y un botón. */
export function BannerAccion({ children, accion }: { children: ReactNode; accion?: ReactNode }) {
  return (
    <div className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-panel border border-magenta-borde border-l-4 border-l-magenta bg-magenta-tinte px-4 py-3 text-[13px] text-magenta">
      <div className="flex items-center gap-2.5">
        <span aria-hidden="true" className="inline-block h-2 w-2 rounded-full bg-magenta" />
        <div>{children}</div>
      </div>
      {accion}
    </div>
  );
}

/** Texto mientras carga. */
export function Cargando({ texto = 'Cargando…' }: { texto?: string }) {
  return (
    <p role="status" className="py-4 text-[13px] text-apagado">
      {texto}
    </p>
  );
}

/** Mensaje cuando una lista no tiene nada que mostrar. */
export function Vacio({ children }: { children: ReactNode }) {
  return <p className="rounded-[5px] bg-superficie-sub px-4 py-6 text-center text-[13px] text-apagado">{children}</p>;
}
