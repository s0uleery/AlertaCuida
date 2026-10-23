// Formatos de /api/relojes (RelojesController).

export type EstadoReloj = 'OPERATIVO' | 'EN_REPARACION' | 'RETIRADO';

/** Cómo se muestra cada estado. */
export const NOMBRE_ESTADO: Record<EstadoReloj, string> = {
  OPERATIVO: 'Operativo',
  EN_REPARACION: 'En reparación',
  RETIRADO: 'Retirado',
};

/** Datos de un reloj, como se escriben y como se guardan. Las fechas van como "2027-03-31". */
export interface DatosReloj {
  identificadorReloj: string;
  modelo: string | null;
  /** Identificador de la SIM LTE */
  iccid: string | null;
  numeroSim: string | null;
  fechaVencimientoPlan: string | null;
  estado: EstadoReloj;
}

/** Fila del inventario. */
export interface RelojConPaciente {
  reloj: { id: string; datos: DatosReloj };
  /** Paciente que lo usa ahora, o null si está libre */
  idPaciente: string | null;
  nombrePaciente: string | null;
}
