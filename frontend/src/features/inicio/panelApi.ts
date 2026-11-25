import { pedir } from '../../api/cliente';
import type { ColorSemaforo, MotivoSemaforo, TipoConexion } from '../relojes/tipos';

/** Totales del panel general. */
export interface TotalesPanel {
  /** Sin cerrar por el equipo (en seguimiento cuenta); sin canceladas ni simulacros; salud y técnicas */
  alertasAbiertas: number;
  /** El reloj terminó la lista y nadie confirmó */
  sinRespuesta: number;
  enSeguimiento: number;
  relojesRojo: number;
  relojesAmarillo: number;
}

/** Reloj de un usuario en el panel, con su semáforo. */
export interface RelojPanel {
  idDispositivo: string;
  identificador: string;
  semaforo: ColorSemaforo;
  motivos: MotivoSemaforo[];
  ultimaSincronizacion: string | null;
  nivelBateria: number | null;
  tipoConexion: TipoConexion | null;
  configuracionPendiente: boolean;
}

/** Un usuario del programa en el panel, ya ordenado por urgencia. */
export interface UsuarioPanel {
  id: string;
  nombres: string;
  apellidos: string;
  alertasAbiertas: number;
  reloj: RelojPanel | null;
}

/** GET /api/panel (ALERTAS:VER). */
export interface Panel {
  totales: TotalesPanel;
  usuarios: UsuarioPanel[];
}

/** Panel general del dashboard. Su lectura queda en la auditoría (LEER PACIENTE). */
export const panelApi = {
  obtener: () => pedir<Panel>('/panel'),
};
