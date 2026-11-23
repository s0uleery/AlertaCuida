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

/** Color del semáforo de un reloj (GET /api/relojes/estado). */
export type ColorSemaforo = 'VERDE' | 'AMARILLO' | 'ROJO' | 'GRIS';

/** Por qué el semáforo no está verde. */
export type MotivoSemaforo =
  | 'SIN_VINCULAR'
  | 'SIN_SENAL'
  | 'PLAN_VENCIDO'
  | 'BATERIA_BAJA'
  | 'NO_PUESTO'
  | 'CONFIGURACION_PENDIENTE'
  | 'ALERTA_TECNICA_ABIERTA'
  | 'ATRASO'
  | 'VERSION_ANTIGUA'
  | 'PLAN_POR_VENCER';

/** Estado técnico de un reloj: su semáforo y lo que informó en su última sincronización. */
export interface EstadoTecnicoReloj {
  idDispositivo: string;
  semaforo: ColorSemaforo;
  /** Vacío si está verde */
  motivos: MotivoSemaforo[];
  /** Instante ISO en UTC; null si no ha sincronizado en su vínculo vigente */
  ultimaSincronizacion: string | null;
  nivelBateria: number | null;
  relojPuesto: boolean | null;
  tipoConexion: 'LTE' | 'WIFI' | null;
  versionApp: string | null;
  configuracionPendiente: boolean;
}

/** Cómo se nombra cada color. */
export const NOMBRE_SEMAFORO: Record<ColorSemaforo, string> = {
  VERDE: 'Funcionando',
  AMARILLO: 'Atención',
  ROJO: 'Sin señal',
  GRIS: 'Sin vincular',
};

/** Cómo se explica cada motivo. */
export const MOTIVO_SEMAFORO: Record<MotivoSemaforo, string> = {
  SIN_VINCULAR: 'Nadie lo usa',
  SIN_SENAL: 'Lleva 4 horas o más sin sincronizar',
  PLAN_VENCIDO: 'El plan de datos de la SIM venció',
  BATERIA_BAJA: 'Batería bajo 20 %',
  NO_PUESTO: 'No está puesto',
  CONFIGURACION_PENDIENTE: 'Aún no aplica la configuración vigente',
  ALERTA_TECNICA_ABIERTA: 'Tiene una alerta técnica abierta',
  ATRASO: 'Se atrasó en sincronizar',
  VERSION_ANTIGUA: 'Su app es más antigua que la de otros relojes',
  PLAN_POR_VENCER: 'El plan de datos vence en 30 días o menos',
};
