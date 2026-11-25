import { pedir } from '../../api/cliente';
import type { Severidad } from '../alertas/tipos';
import type { Calendario, DiaHabil, Simulacion } from './tipos';

/** Llamadas al backend para el calendario hábil. Consultar basta con tener sesión. */
export const calendarioApi = {
  /** Horario hábil y feriados vigentes. */
  consultar: () => pedir<Calendario>('/calendario'),

  /** Reemplaza la jornada hábil (ADMINISTRACION:EDITAR). Los días que no van dejan de ser hábiles. */
  guardarHorario: (horarioHabil: DiaHabil[]) =>
    pedir<void>('/calendario/horario', { method: 'PUT', body: JSON.stringify({ horarioHabil }) }),

  /**
   * Qué pasaría con una alerta de salud ese día, a esa hora y con esa gravedad (ALERTAS:VER).
   *
   * @param fecha "2026-11-25"
   * @param hora "15:30", hora de Chile
   * @param idPaciente opcional: muestra a qué contactos suyos avisaría el reloj
   */
  simular: (fecha: string, hora: string, gravedad: Severidad, idPaciente: string | null) => {
    const parametros = new URLSearchParams({ fecha, hora, gravedad });
    if (idPaciente) parametros.set('idPaciente', idPaciente);
    return pedir<Simulacion>(`/calendario/simulacion?${parametros}`);
  },
};
