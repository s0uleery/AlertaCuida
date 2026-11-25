// Clases de Tailwind que se repiten en todas las pantallas, con el sistema de diseño del PRLAC (index.css).
// Para piezas más completas (botones con variantes, tablas, diálogos) están los componentes de comun/ui.

/** Caja de texto, lista o casilla de contraseña. */
export const CLASE_CAMPO =
  'mt-1 min-h-[34px] w-full rounded-[5px] border border-linea-oscura bg-superficie px-2.5 py-1.5 text-[13px] text-texto focus:border-teal focus:outline-2 focus:outline-teal-tinte disabled:bg-superficie-sub';

/** Texto sobre cada campo. */
export const CLASE_ETIQUETA = 'text-xs font-bold text-navy';

/** Botón de la acción principal de la pantalla (turquesa institucional). */
export const CLASE_BOTON_PRINCIPAL =
  'inline-flex items-center justify-center gap-1.5 rounded-[5px] border border-teal bg-teal px-3.5 py-[7px] text-[13px] font-bold text-white hover:border-teal-hover hover:bg-teal-hover disabled:cursor-not-allowed disabled:opacity-45';

/** Botón de una acción secundaria (contorno). */
export const CLASE_BOTON_SECUNDARIO =
  'inline-flex items-center justify-center gap-1.5 rounded-[5px] border border-linea-oscura bg-superficie px-3.5 py-[7px] text-[13px] font-medium text-navy hover:bg-superficie-sub disabled:cursor-not-allowed disabled:opacity-45';

/** Mensaje de error de un formulario. */
export const CLASE_ERROR =
  'rounded-[5px] border border-magenta-borde bg-magenta-tinte px-3 py-2 text-[13px] text-magenta';

/** Tarjeta blanca donde va cada bloque de contenido. */
export const CLASE_TARJETA = 'rounded-panel border border-linea bg-superficie p-5';
