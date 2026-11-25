import type { ReactNode } from 'react';

/** Una celda de la tira de métricas: título, cifra grande y nota. */
export interface Metrica {
  titulo: string;
  valor: ReactNode;
  nota?: ReactNode;
  /** Resalta la cifra en magenta (algo que requiere acción) */
  destacada?: boolean;
}

/** Tira de indicadores del mockup: celdas con borde superior navy y la cifra en mono. */
export function TiraMetricas({ metricas }: { metricas: Metrica[] }) {
  return (
    <div className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
      {metricas.map((m) => (
        <div key={m.titulo} className="rounded-panel border border-linea border-t-[3px] border-t-navy bg-superficie px-4 py-3">
          <small className="block text-[11px] font-bold tracking-wide text-apagado uppercase">{m.titulo}</small>
          <b className={`cifra mt-1 block text-2xl font-semibold ${m.destacada ? 'text-magenta' : 'text-navy'}`}>
            {m.valor}
          </b>
          {m.nota && <span className="text-xs text-apagado">{m.nota}</span>}
        </div>
      ))}
    </div>
  );
}

/**
 * Anillo de progreso (conic-gradient con máscara, como el mockup).
 *
 * @param porcentaje de 0 a 100
 */
export function Anillo({ porcentaje, etiqueta }: { porcentaje: number; etiqueta: string }) {
  const p = Math.max(0, Math.min(100, Math.round(porcentaje)));
  return (
    <div className="relative grid h-[82px] w-[82px] flex-none place-items-center" role="img" aria-label={`${etiqueta}: ${p} %`}>
      <div
        className="absolute inset-0 rounded-full"
        style={{
          background: `conic-gradient(var(--color-teal) 0 ${p}%, var(--color-linea) 0)`,
          mask: 'radial-gradient(circle 27px, transparent 98%, #000 100%)',
          WebkitMask: 'radial-gradient(circle 27px, transparent 98%, #000 100%)',
        }}
      />
      <span className="cifra text-sm font-semibold text-navy">{p}%</span>
    </div>
  );
}

/** Tono de una fila de leyenda. */
export type TonoBarra = 'teal' | 'gris' | 'naranja' | 'verde' | 'magenta';

const BARRA: Record<TonoBarra, string> = {
  teal: 'border-l-teal bg-teal-tinte',
  gris: 'border-l-[#8fa1ad] bg-superficie-sub',
  naranja: 'border-l-naranja bg-naranja-tinte',
  verde: 'border-l-verde bg-verde-tinte',
  magenta: 'border-l-magenta bg-magenta-tinte',
};

/** Fila de leyenda con borde de color: "{cifra} {texto}". Si se da total, dibuja además una barra proporcional. */
export function BarraLeyenda({
  tono,
  valor,
  texto,
  total,
}: {
  tono: TonoBarra;
  valor: number;
  texto: ReactNode;
  total?: number;
}) {
  const proporcion = total && total > 0 ? Math.round((valor / total) * 100) : null;
  return (
    <div className={`rounded border-l-[3px] px-3 py-[7px] text-[12.5px] ${BARRA[tono]}`}>
      <div className="flex items-center justify-between gap-2">
        <span>
          <b className="cifra font-semibold text-navy">{valor}</b> {texto}
        </span>
        {proporcion !== null && <span className="cifra text-apagado">{proporcion}%</span>}
      </div>
      {proporcion !== null && (
        <div className="mt-1 h-1 overflow-hidden rounded bg-white/70" aria-hidden="true">
          <div className="h-full bg-current text-navy/40" style={{ width: `${proporcion}%` }} />
        </div>
      )}
    </div>
  );
}

/** Tarjeta de resumen con anillo y leyendas, como "Estado de resolución" del mockup. */
export function ResumenAnillo({
  titulo,
  porcentaje,
  etiqueta,
  children,
}: {
  titulo: string;
  porcentaje: number;
  etiqueta: string;
  children: ReactNode;
}) {
  return (
    <section className="rounded-panel border border-linea bg-superficie p-5">
      <h2 className="mb-3 text-[14.5px] font-bold text-navy">{titulo}</h2>
      <div className="flex items-center gap-4">
        <Anillo porcentaje={porcentaje} etiqueta={etiqueta} />
        <div className="grid flex-1 gap-2">{children}</div>
      </div>
    </section>
  );
}
