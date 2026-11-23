import { pedir } from '../../api/cliente';
import type { RegistroAuditoria } from './tipos';

/** Cuántos registros se piden; el filtro y la búsqueda se hacen aquí sobre ellos. */
const LIMITE = 500;

/** Llamadas al backend para el registro de auditoría. */
export const auditoriaApi = {
  /**
   * Los últimos registros, del más nuevo al más antiguo.
   *
   * @param incluirLecturas si también se traen las consultas de datos, que son muchas más que los cambios
   */
  recientes: (incluirLecturas: boolean) =>
    pedir<RegistroAuditoria[]>(`/auditoria?limite=${LIMITE}&incluirLecturas=${incluirLecturas}`),
};
