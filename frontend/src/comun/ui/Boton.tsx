import type { ButtonHTMLAttributes } from 'react';

/** Las variantes del mockup: principal (turquesa), normal (navy), contorno, peligro (magenta) y enlace. */
export type VarianteBoton = 'principal' | 'normal' | 'contorno' | 'peligro' | 'enlace';

const BASE =
  'inline-flex items-center justify-center gap-1.5 rounded-[5px] border font-medium transition-colors ' +
  'disabled:cursor-not-allowed disabled:opacity-45';

const VARIANTE: Record<VarianteBoton, string> = {
  principal: 'border-teal bg-teal font-bold text-white hover:enabled:border-teal-hover hover:enabled:bg-teal-hover',
  normal: 'border-navy bg-navy text-white hover:enabled:bg-navy-hover',
  contorno: 'border-linea-oscura bg-superficie text-navy hover:enabled:bg-superficie-sub',
  peligro: 'border-magenta bg-magenta text-white hover:enabled:bg-magenta-hover',
  enlace: 'border-transparent bg-transparent font-bold text-teal-oscuro underline-offset-2 hover:underline',
};

/**
 * Clases de un botón, para usarlas también en enlaces (Link) que se ven como botón.
 *
 * @param chico versión compacta, para tablas y barras de herramientas
 */
export function claseBoton(variante: VarianteBoton = 'normal', chico = false): string {
  const tamano = variante === 'enlace' ? 'p-0 text-[13px]' : chico ? 'px-2.5 py-[5px] text-[12.5px]' : 'px-3.5 py-[7px] text-[13px]';
  return `${BASE} ${VARIANTE[variante]} ${tamano}`;
}

/** Botón con las variantes del sistema de diseño. Por defecto es type="button" para no enviar formularios. */
export function Boton({
  variante = 'normal',
  chico = false,
  className = '',
  type = 'button',
  ...resto
}: ButtonHTMLAttributes<HTMLButtonElement> & { variante?: VarianteBoton; chico?: boolean }) {
  return <button type={type} className={`${claseBoton(variante, chico)} ${className}`} {...resto} />;
}
