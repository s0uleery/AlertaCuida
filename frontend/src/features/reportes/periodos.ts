/** Períodos de un reporte por usuario; el valor va en la ruta (/reportes/:id/diario). */
export const PERIODOS = [
  { valor: 'diario', etiqueta: 'Diario' },
  { valor: 'semanal', etiqueta: 'Semanal' },
] as const;

export type Periodo = (typeof PERIODOS)[number]['valor'];
