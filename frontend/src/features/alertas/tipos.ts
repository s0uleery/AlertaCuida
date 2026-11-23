// Formatos de las alertas. Siguen la tabla alerta y la vista alerta_estado (migración V5).

/** Gravedad actual: la de llegada, o GRAVE si se agravó. */
export type Severidad = 'LEVE' | 'MEDIA' | 'GRAVE';

/** Qué detectó el reloj (o el servidor, en las técnicas). */
export type TipoEvento =
  | 'CAIDA'
  | 'FC'
  | 'SPO2'
  | 'FC_SPO2'
  | 'TENDENCIA'
  | 'INACTIVIDAD'
  | 'ZONA_SEGURA'
  | 'BOTON_AYUDA'
  | 'SILENCIO_DATOS'
  | 'BATERIA_BAJA'
  | 'RELOJ_NO_PUESTO'
  | 'EXCESO_ALERTAS';

/** A quién está avisando el reloj, o cómo terminó ese recorrido. Lo calcula la vista alerta_estado. */
export type EstadoContacto =
  'RECIBIDA' | 'AVISANDO_CONTACTO' | 'CONFIRMADA' | 'SIN_RESPUESTA' | 'RESUELTA' | 'PENDIENTE_DIA_HABIL' | 'CANCELADA';

/** De qué habla la alerta: la salud de la persona o el reloj. */
export type CategoriaAlerta = 'SALUD' | 'TECNICA';

/** Lo que ha hecho el equipo PRLAC con la alerta. */
export type EstadoAtencion = 'SIN_ASIGNAR' | 'EN_ATENCION' | 'EN_SEGUIMIENTO' | 'RESUELTA';

/** Fila de las listas de alertas. */
export interface ResumenAlerta {
  id: string;
  /** Folio legible (#142) */
  numero: number;
  paciente: {
    id: string;
    nombres: string;
    apellidos: string;
    edad: number | null;
  };
  tipoEvento: TipoEvento;
  severidad: Severidad;
  estadoContacto: EstadoContacto;
  estadoAtencion: EstadoAtencion;
  /** Nombre de quien tomó el caso; null si nadie */
  responsable: string | null;
  /** Instante ISO en UTC en que llegó al servidor */
  recepcion: string;
  /** true si nadie de la lista confirmó y sigue abierta: va primero en la lista */
  prioridadMaxima: boolean;
  /** Salud o técnica: decide la pestaña */
  categoria: CategoriaAlerta;
  /** true si es un ensayo, no una emergencia real */
  simulacro: boolean;
  /** Último contacto del paciente al que el reloj pidió avisar; null si ninguno */
  contactoEnCurso: string | null;
}

/** Cómo cerró el equipo una alerta de salud. Las técnicas no se clasifican. */
export type ClasificacionAlerta = 'POSITIVA' | 'FALSA_ALARMA';

/** Por dónde se avisó a una persona. */
export type Canal = 'WHATSAPP' | 'CORREO';

/** Qué pasó en cada paso de la línea de tiempo. */
export type TipoPaso =
  | 'ALERTA_RECIBIDA'
  | 'ALERTA_AGRAVADA'
  | 'ALERTA_CANCELADA'
  | 'SALTO_CONTACTO'
  | 'AVISO_ENVIADO'
  | 'AVISO_ENTREGADO'
  | 'AVISO_LEIDO'
  | 'AVISO_FALLIDO'
  | 'RESPUESTA_CONTACTO'
  | 'SIN_RESPUESTA'
  | 'CASO_ASUMIDO'
  | 'SEGUIMIENTO'
  | 'ALERTA_RESUELTA'
  | 'SEGUIMIENTO_CERRADO'
  | 'FCM_PENDIENTE'
  | 'FCM_ENVIADO'
  | 'FCM_RECIBIDO'
  | 'FCM_NO_CONFIRMADO';

/** Un paso de la línea de tiempo. Los campos que no corresponden a ese paso vienen en null. */
export interface PasoAlerta {
  /** Instante ISO en UTC. Lo que informa el reloj usa la hora del reloj */
  fechaHora: string;
  tipo: TipoPaso;
  /** A quién se avisó: una persona de la lista o una cuenta del equipo */
  contacto: string | null;
  canal: Canal | null;
  /** Vuelta de la lista (1 o 2); null en los avisos al equipo */
  ronda: number | null;
  /** Motivo del fallo, respuesta del contacto, tipo de aviso al reloj, clasificación o nota de seguimiento */
  detalle: string | null;
  /** Profesional que hizo el paso */
  usuario: string | null;
}

/** Cómo se cerró la alerta. */
export interface CierreAlerta {
  fechaHora: string;
  /** Nombre de quien la resolvió; null si la cerró el sistema */
  resolutor: string | null;
  /** null en las técnicas */
  clasificacion: ClasificacionAlerta | null;
  observaciones: string | null;
}

/** Una lectura que llevó al reloj a alertar. */
export interface LecturaEvidencia {
  tipo: 'FC' | 'SPO2';
  /** En latidos por minuto o en % */
  valor: number;
  /** Instante ISO en UTC, en la hora del reloj */
  fechaHoraMedicion: string;
}

/** Posición GPS que mandó el reloj con la alerta. */
export interface UbicacionAlerta {
  latitud: number;
  longitud: number;
  /** Radio de error; null si el reloj no lo informó */
  precisionMetros: number | null;
  fechaHoraMedicion: string;
}

/** Lo que explica la alerta. Cada campo es null si esa alerta no lo trae. */
export interface EvidenciaAlerta {
  /** Vacía si no trae */
  lecturas: LecturaEvidencia[];
  /** En % */
  nivelBateria: number | null;
  /** En las caídas */
  segundosInmovilidad: number | null;
  ubicacion: UbicacionAlerta | null;
  /** Exceso de alertas: cuántas llegaron en la ventana */
  alertasEnVentana: number | null;
  ventanaMinutos: number | null;
  /** Reloj no puesto: desde cuándo */
  noPuestoDesde: string | null;
  /** Silencio de datos: la última sincronización; null también si nunca sincronizó */
  ultimaSincronizacion: string | null;
}

/** Qué no calzó entre la alerta y la configuración vigente al recibirla. */
export type CodigoDiscrepancia = 'VERSION_DISTINTA' | 'MODULO_APAGADO' | 'GRAVEDAD_DISTINTA' | 'LECTURAS_EN_RANGO';

/** Una discrepancia, con la explicación que guardó el servidor. */
export interface Discrepancia {
  codigo: CodigoDiscrepancia;
  detalle: string;
}

/** Ficha de una alerta: los campos de la lista más su cierre, su línea de tiempo, su evidencia y sus discrepancias. */
export interface DetalleAlerta extends ResumenAlerta {
  /** null mientras siga abierta */
  atencion: CierreAlerta | null;
  /** Del paso más antiguo al más reciente */
  lineaTiempo: PasoAlerta[];
  evidencia: EvidenciaAlerta;
  discrepancias: Discrepancia[];
}
