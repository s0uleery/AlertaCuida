import { useRef, type KeyboardEvent, type ReactNode } from 'react';

/** Una pestaña: su clave, su texto y, si se quiere, un contador. */
export interface Pestana<C extends string> {
  clave: C;
  texto: ReactNode;
}

/**
 * Pestañas del mockup (la activa subrayada en turquesa), accesibles: role=tablist y flechas izquierda/derecha
 * para moverse. El contenido de cada pestaña lo dibuja quien las usa.
 */
export function Pestanas<C extends string>({
  pestanas,
  activa,
  alCambiar,
  etiqueta,
}: {
  pestanas: Pestana<C>[];
  activa: C;
  alCambiar: (clave: C) => void;
  etiqueta: string;
}) {
  const botones = useRef<(HTMLButtonElement | null)[]>([]);

  /** Las flechas mueven la pestaña activa y el foco. */
  function teclado(evento: KeyboardEvent, indice: number) {
    const paso = evento.key === 'ArrowRight' ? 1 : evento.key === 'ArrowLeft' ? -1 : 0;
    if (paso === 0) return;
    evento.preventDefault();
    const siguiente = (indice + paso + pestanas.length) % pestanas.length;
    alCambiar(pestanas[siguiente].clave);
    botones.current[siguiente]?.focus();
  }

  return (
    <div role="tablist" aria-label={etiqueta} className="mb-5 flex gap-1 overflow-x-auto border-b border-linea">
      {pestanas.map((pestana, i) => {
        const esActiva = pestana.clave === activa;
        return (
          <button
            key={pestana.clave}
            ref={(boton) => {
              botones.current[i] = boton;
            }}
            type="button"
            role="tab"
            aria-selected={esActiva}
            tabIndex={esActiva ? 0 : -1}
            onClick={() => alCambiar(pestana.clave)}
            onKeyDown={(e) => teclado(e, i)}
            className={`-mb-px inline-flex items-center gap-2 border-b-[3px] px-3.5 py-2.5 text-[13px] whitespace-nowrap ${
              esActiva
                ? 'border-teal font-bold text-navy'
                : 'border-transparent text-apagado hover:bg-teal-tinte/60 hover:text-navy'
            }`}
          >
            {pestana.texto}
          </button>
        );
      })}
    </div>
  );
}

/** Control segmentado del mockup (Diario/Semanal, flujo de atención): botones pegados, el activo en navy. */
export function Segmentado<C extends string>({
  opciones,
  activa,
  alCambiar,
  etiqueta,
  hechas = [],
}: {
  opciones: { clave: C; texto: ReactNode }[];
  activa: C;
  /** Sin alCambiar, solo muestra (por ejemplo, el estado del flujo) */
  alCambiar?: (clave: C) => void;
  etiqueta: string;
  /** Pasos ya superados: se muestran en turquesa */
  hechas?: C[];
}) {
  return (
    <div role="group" aria-label={etiqueta} className="inline-flex flex-wrap overflow-hidden rounded-[5px] border border-linea-oscura">
      {opciones.map((opcion, i) => {
        const esActiva = opcion.clave === activa;
        const hecha = hechas.includes(opcion.clave);
        const clase = `px-3.5 py-1.5 text-[12.5px] ${i > 0 ? 'border-l border-linea-oscura' : ''} ${
          esActiva ? 'bg-navy font-bold text-white' : hecha ? 'bg-superficie font-bold text-teal-oscuro' : 'bg-superficie text-apagado'
        }`;
        return alCambiar ? (
          <button
            key={opcion.clave}
            type="button"
            aria-pressed={esActiva}
            onClick={() => alCambiar(opcion.clave)}
            className={`${clase} hover:enabled:bg-superficie-sub ${esActiva ? 'hover:enabled:bg-navy' : ''}`}
          >
            {opcion.texto}
          </button>
        ) : (
          <span key={opcion.clave} aria-current={esActiva ? 'step' : undefined} className={clase}>
            {hecha && <span aria-hidden="true">✓ </span>}
            {opcion.texto}
          </span>
        );
      })}
    </div>
  );
}
