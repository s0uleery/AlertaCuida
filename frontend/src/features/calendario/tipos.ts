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

/** Un contacto en el orden en que el reloj le avisaría (GET /api/calendario/simulacion). */
export interface PasoSimulado {
  orden: number;
  nombre: string;
  /** TERCERO_SIGNIFICATIVO, CARABINEROS, SEGURIDAD_CIUDADANA, CESFAM… */
  tipo: string;
  parentesco: string | null;
  cuidadorPrincipal: boolean;
  canales: ('WHATSAPP' | 'CORREO')[];
}

/** Qué pasaría con una alerta de salud, con las reglas que el sistema aplica hoy. */
export interface Simulacion {
  /** Dentro del horario hábil y no es feriado */
  habil: boolean;
  feriado: boolean;
  /** Leve fuera de horario: no avisa a nadie y espera al día hábil */
  esperaDiaHabil: boolean;
  /** Se avisa a los coordinadores de la oficina PRLAC */
  avisoOficina: boolean;
  /** A quiénes avisaría el reloj, en orden; vacía sin usuario o si espera al día hábil */
  contactos: PasoSimulado[];
}
