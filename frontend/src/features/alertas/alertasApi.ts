import { pedir } from '../../api/cliente';
import type { ResumenAlerta } from './tipos';

/** Llamadas al backend para alertas. Exigen ALERTAS:VER. */
export const alertasApi = {
  /**
   * Hasta 200 alertas no canceladas, simulacros incluidos: primero las que quedaron sin respuesta y después
   * las más recientes.
   */
  listar: () => pedir<ResumenAlerta[]>('/alertas'),

  /** Las mismas alertas, solo por fecha de llegada: la más reciente primero. */
  recientes: async (): Promise<ResumenAlerta[]> =>
    (await alertasApi.listar()).sort((a, b) => b.recepcion.localeCompare(a.recepcion)),
};
