import { pedir } from '../../api/cliente';
import type { RegistroAuditoria } from './tipos';

/** Cuántos registros se piden; el filtro y la búsqueda se hacen aquí sobre ellos. */
const LIMITE = 500;

/** Llamadas al backend para el registro de auditoría. */
export const auditoriaApi = {
  /**
   * Los últimos registros, del más nuevo al más antiguo.
   *
   * Falta en el backend: GET /api/auditoria?limite=N con @PreAuthorize(ADMINISTRACION:VER) que lea
   * registro_auditoria (hoy solo se escribe, con RegistroAuditoriaJdbc) unida a usuario para el nombre
   * de quien hizo la acción. Devuelve RegistroAuditoria[]. Conviene no incluir las acciones LEER,
   * que son muchas, o agregar un parámetro para pedirlas aparte.
   */
  recientes: () => pedir<RegistroAuditoria[]>(`/auditoria?limite=${LIMITE}`),
};
