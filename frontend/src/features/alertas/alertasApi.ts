import { pedir } from '../../api/cliente';
import type { ClasificacionAlerta, DetalleAlerta, ResumenAlerta } from './tipos';

/** Llamadas al backend para alertas. Ver exige ALERTAS:VER; actuar, ALERTAS:EDITAR. */
export const alertasApi = {
  /**
   * Hasta 200 alertas no canceladas, simulacros incluidos: primero las que quedaron sin respuesta y después
   * las más recientes.
   */
  listar: () => pedir<ResumenAlerta[]>('/alertas'),

  /** Las mismas alertas, solo por fecha de llegada: la más reciente primero. */
  recientes: async (): Promise<ResumenAlerta[]> =>
    (await alertasApi.listar()).sort((a, b) => b.recepcion.localeCompare(a.recepcion)),

  /** Ficha de una alerta: línea de tiempo, evidencia, discrepancias y cierre. */
  detalle: (id: string) => pedir<DetalleAlerta>(`/alertas/${id}`),

  /** Quien llama queda a cargo. 409 si otra persona ya la tomó, si está cerrada o si la canceló el usuario. */
  asumir: (id: string) => pedir<void>(`/alertas/${id}/asumir`, { method: 'POST' }),

  /**
   * Cierra la alerta y, si la envió el reloj, le avisa para que deje de avisar a los contactos.
   *
   * @param clasificacion obligatoria en las de salud; null en las técnicas
   * @param observaciones obligatorias siempre
   */
  resolver: (id: string, clasificacion: ClasificacionAlerta | null, observaciones: string) =>
    pedir<void>(`/alertas/${id}/resolver`, { method: 'POST', body: JSON.stringify({ clasificacion, observaciones }) }),

  /** Termina el seguimiento de una alerta POSITIVA. */
  cerrarSeguimiento: (id: string) => pedir<void>(`/alertas/${id}/seguimiento/cerrar`, { method: 'POST' }),
};
