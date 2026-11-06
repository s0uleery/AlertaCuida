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
  estadoAtencion: EstadoAtencion;
  /** Nombre de quien tomó el caso; null si nadie */
  responsable: string | null;
  /** Instante ISO en UTC en que llegó al servidor */
  recepcion: string;
}
