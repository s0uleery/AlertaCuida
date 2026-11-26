import type { ClasificacionAlerta, Severidad, TipoEvento } from '../alertas/tipos';

/** Lo que responde un contacto del paciente: los mismos valores que el botón del WhatsApp. */
export type RespuestaContacto = 'VOY_EN_CAMINO' | 'NO_PUEDO';

/** Lo que dice el contacto después de llamar al paciente. */
export type ResultadoLlamadaContacto = 'CONTESTA' | 'NO_CONTESTA';

/** Cómo resultó la llamada de la oficina al reloj (N1-02). */
export type ResultadoLlamadaOficina = 'BIEN' | 'NECESITA_ATENCION' | 'NO_CONTESTA';

/** Cómo encontró el personal a la persona (N1-09). */
export type ResultadoVisita = 'BIEN' | 'APOYO_NO_URGENTE' | 'EMERGENCIA';

/** En qué va la oficina con la alerta: decide qué pantalla de los pasos se muestra. */
export type EtapaOficina =
  | 'SIN_TOMAR'
  | 'A_CARGO_DE_OTRO'
  | 'LLAMAR'
  | 'CONFIRMAR_EVENTO'
  | 'COORDINAR_VISITA'
  | 'SIN_DISPONIBILIDAD'
  | 'ESPERANDO_LLEGADA'
  | 'RESULTADO_VISITA'
  | 'EMERGENCIA'
  | 'CERRADA';

/** Cada botón de los pasos de la oficina (los mismos de los mensajes de WhatsApp N1). */
export type PasoOficina =
  | 'TOMAR'
  | 'PACIENTE_BIEN'
  | 'NECESITA_ATENCION'
  | 'NO_CONTESTA'
  | 'OCURRIO'
  | 'FALSA_ALARMA'
  | 'PERSONAL_ASIGNADO'
  | 'SIN_DISPONIBILIDAD'
  | 'LLEGO'
  | 'SIGUE_EN_CAMINO'
  | 'VISITA_BIEN'
  | 'APOYO_NO_URGENTE'
  | 'EMERGENCIA'
  | 'TRASLADADA'
  | 'ATENDIDA_EN_CASA'
  | 'OTRO_RESULTADO'
  | 'COMENTARIO';

/** Lo que ve un coordinador en su enlace: los pasos de la oficina. Las fechas van en UTC y son null si no hay. */
export interface VistaOficina {
  /** Para abrir la ficha en el dashboard (pide iniciar sesión). */
  idAlerta: string;
  etapa: EtapaOficina;
  /** Nombre de quien la tiene a cargo; null si nadie. */
  responsable: string | null;
  /** Si es quien abrió el enlace: solo esa persona da los pasos. */
  esResponsable: boolean;
  resultadoLlamada: ResultadoLlamadaOficina | null;
  personalAsignado: string | null;
  sinDisponibilidad: string | null;
  llegada: string | null;
  resultadoVisita: ResultadoVisita | null;
  /** Si el personal lleva más del plazo sin llegar: se ofrece "Sigue en camino". */
  recordatorio: boolean;
  cierre: string | null;
  clasificacion: ClasificacionAlerta | null;
  /** Último contacto del paciente al que avisó el reloj, y cuándo (N1-06). */
  ultimoContacto: string | null;
  avisoUltimoContacto: string | null;
}

/** Lo que ve quien abre el enlace (POST /api/respuestas-contacto/consulta). */
export interface VistaRespuesta {
  /** Folio de la alerta (#142). */
  numero: number;
  /** Nombre corto del paciente; null si no tiene (se usa el folio). */
  alias: string | null;
  tipo: TipoEvento;
  severidad: Severidad;
  /** Cuándo se detectó, en UTC. */
  fechaHora: string;
  /** Lo que ya respondió; null si aún no responde. */
  respuesta: RespuestaContacto | null;
  /** Cuándo respondió, en UTC; null si aún no responde. */
  fechaRespuesta: string | null;
  /** Si el equipo ya resolvió o canceló la alerta: ya no hace falta responder. */
  cerrada: boolean;
  /** Número de la SIM del reloj, para "Llamar al paciente"; null si la alerta está cerrada o no hay número. */
  telefonoPaciente: string | null;
  /** Cuándo avisó que llegó donde el paciente, en UTC; null si no lo ha hecho. */
  llegada: string | null;
  /** Lo que dijo el contacto después de llamar; null si aún no lo dice. */
  resultadoLlamada: ResultadoLlamadaContacto | null;
  /** Los pasos de la oficina, si el enlace es de un coordinador; null si es de un contacto del paciente. */
  oficina: VistaOficina | null;
}
