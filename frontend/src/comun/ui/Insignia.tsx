import type { ReactNode } from 'react';

/** Tonos de insignia: los de gravedad (grave, media, leve) y otros de estado. */
export type TonoInsignia = 'grave' | 'media' | 'leve' | 'nueva' | 'neutra' | 'cerrada' | 'teal';

const TONO: Record<TonoInsignia, string> = {
  grave: 'border-magenta-borde bg-magenta-tinte text-magenta',
  media: 'border-naranja-borde bg-naranja-tinte text-naranja-texto',
  leve: 'border-verde-borde bg-verde-tinte text-verde-texto',
  nueva: 'border-teal-borde bg-teal-tinte font-bold text-navy',
  neutra: 'border-linea bg-superficie-sub text-texto',
  cerrada: 'border-verde-borde bg-verde-tinte text-verde-texto',
  teal: 'border-teal-borde bg-teal-tinte text-navy',
};

const PUNTO: Partial<Record<TonoInsignia, string>> = {
  grave: 'bg-magenta',
  media: 'bg-naranja',
  leve: 'bg-verde',
};

/** Insignia chica de estado. Las de gravedad llevan un punto de color, como en el mockup. */
export function Insignia({ tono = 'neutra', children }: { tono?: TonoInsignia; children: ReactNode }) {
  const punto = PUNTO[tono];
  const peso = punto ? 'font-bold' : '';
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded border px-2 py-0.5 text-[11.5px] whitespace-nowrap ${peso} ${TONO[tono]}`}
    >
      {punto && <span aria-hidden="true" className={`inline-block h-1.5 w-1.5 rounded-full ${punto}`} />}
      {children}
    </span>
  );
}
