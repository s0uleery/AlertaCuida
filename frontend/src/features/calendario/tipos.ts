// Formatos de /api/calendario (CalendarioController).

export type DiaSemana = 'LUNES' | 'MARTES' | 'MIERCOLES' | 'JUEVES' | 'VIERNES' | 'SABADO' | 'DOMINGO';

/** Días en el orden de la semana, con su nombre para mostrar. */
export const DIAS: { dia: DiaSemana; nombre: string }[] = [
  { dia: 'LUNES', nombre: 'Lunes' },
  { dia: 'MARTES', nombre: 'Martes' },
  { dia: 'MIERCOLES', nombre: 'Miércoles' },
  { dia: 'JUEVES', nombre: 'Jueves' },
  { dia: 'VIERNES', nombre: 'Viernes' },
  { dia: 'SABADO', nombre: 'Sábado' },
  { dia: 'DOMINGO', nombre: 'Domingo' },
];

/** Horario de un día hábil, en hora de Chile ("08:30"): el inicio cuenta, el fin ya no. */
export interface DiaHabil {
  dia: DiaSemana;
  inicio: string;
  fin: string;
}

export interface Feriado {
  /** "2026-09-18" */
  fecha: string;
  nombre: string;
}

export interface Calendario {
  zonaHoraria: string;
  /** Un tramo por día hábil; los días que no aparecen no son hábiles */
  horarioHabil: DiaHabil[];
  /** Ordenados por fecha */
  feriados: Feriado[];
}
