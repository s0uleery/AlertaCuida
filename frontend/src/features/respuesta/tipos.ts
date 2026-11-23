import type { Severidad, TipoEvento } from '../alertas/tipos';

/** Lo que responde un contacto del paciente: los mismos valores que el botón del WhatsApp. */
export type RespuestaContacto = 'VOY_EN_CAMINO' | 'NO_PUEDO';

/** Lo que ve el contacto en la página de su enlace (POST /api/respuestas-contacto/consulta). */
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
}
