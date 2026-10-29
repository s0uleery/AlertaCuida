import type { Rol } from './tipos';

/** Roles de la tabla rol (V6__catalogos_iniciales.sql), con lo que puede hacer cada uno al partir. */
export const ROLES: { valor: Rol; etiqueta: string; descripcion: string }[] = [
  { valor: 'PERSONAL', etiqueta: 'Personal', descripcion: 'Ve y atiende alertas' },
  { valor: 'COORDINADOR', etiqueta: 'Coordinador', descripcion: 'Alertas, usuarios del programa y reportes' },
  { valor: 'ADMINISTRADOR', etiqueta: 'Administrador', descripcion: 'Todo, incluidas las cuentas del equipo' },
];

/** Nombre para mostrar de un rol; si llega uno que no está en la lista, su código. */
export function nombreRol(codigo: string): string {
  return ROLES.find((rol) => rol.valor === codigo)?.etiqueta ?? codigo;
}
