import { pedir } from '../../api/cliente';
import type { Calendario } from './tipos';

/** Llamadas al backend para el calendario hábil. Basta con tener sesión. */
export const calendarioApi = {
  /** Horario hábil y feriados vigentes. */
  consultar: () => pedir<Calendario>('/calendario'),
};
