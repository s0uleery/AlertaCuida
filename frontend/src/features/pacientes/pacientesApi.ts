import { pedir } from '../../api/cliente';
import type { EstadoReloj } from '../relojes/tipos';
import type { DatosPaciente, DetallePaciente, ResumenPaciente, TokenReloj } from './tipos';

/** Llamadas al backend para pacientes y su reloj. Exigen permisos de USUARIOS. */
export const pacientesApi = {
  /** Pacientes con su edad y reloj actual. */
  listar: (incluirBajas: boolean) => pedir<ResumenPaciente[]>(`/pacientes?incluirBajas=${incluirBajas}`),

  /** Ficha con historial de relojes. */
  detalle: (id: string) => pedir<DetallePaciente>(`/pacientes/${id}`),

  /** Registra un paciente; devuelve su identificador. */
  registrar: (datos: DatosPaciente) =>
    pedir<{ id: string }>('/pacientes', { method: 'POST', body: JSON.stringify(datos) }),

  /** Reemplaza su identidad. */
  editar: (id: string, datos: DatosPaciente) =>
    pedir<void>(`/pacientes/${id}`, { method: 'PUT', body: JSON.stringify(datos) }),

  /** Baja con fecha de hoy; cierra su reloj. */
  darDeBaja: (id: string) => pedir<void>(`/pacientes/${id}/baja`, { method: 'POST' }),

  /** Entrega un reloj a un paciente que no tiene. */
  vincular: (id: string, idDispositivo: string) =>
    pedir<TokenReloj>(`/pacientes/${id}/reloj`, { method: 'POST', body: JSON.stringify({ idDispositivo }) }),

  /** Cambia su reloj por otro; el anterior puede quedar en otro estado (ej. EN_REPARACION). */
  reemplazar: (id: string, idDispositivo: string, estadoRelojAnterior: EstadoReloj | null) =>
    pedir<TokenReloj>(`/pacientes/${id}/reloj/reemplazo`, {
      method: 'POST',
      body: JSON.stringify({ idDispositivo, estadoRelojAnterior }),
    }),

  /** Le retira el reloj; su token deja de servir. */
  desvincular: (id: string) => pedir<void>(`/pacientes/${id}/reloj`, { method: 'DELETE' }),

  /** Otro token para el mismo reloj; el anterior deja de servir. */
  nuevoToken: (id: string) => pedir<TokenReloj>(`/pacientes/${id}/reloj/token`, { method: 'POST' }),
};
