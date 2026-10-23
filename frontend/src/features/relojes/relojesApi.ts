import { pedir } from '../../api/cliente';
import type { DatosReloj, RelojConPaciente } from './tipos';

/** Llamadas al backend para el inventario de relojes (1.2.2). Exigen permisos de USUARIOS. */
export const relojesApi = {
  /** Todos los relojes con el paciente que usa cada uno. */
  listar: () => pedir<RelojConPaciente[]>('/relojes'),

  /** Registra un reloj; devuelve su identificador interno. */
  registrar: (datos: DatosReloj) =>
    pedir<{ id: string }>('/relojes', { method: 'POST', body: JSON.stringify(datos) }),

  /** Reemplaza los datos del reloj. */
  editar: (id: string, datos: DatosReloj) =>
    pedir<void>(`/relojes/${id}`, { method: 'PUT', body: JSON.stringify(datos) }),
};
