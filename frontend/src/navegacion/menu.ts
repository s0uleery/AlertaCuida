import type { Permiso } from '../features/sesion/tipos';

/**
 * Secciones del dashboard y el permiso que exige cada una. El menú y las rutas protegidas salen de aquí:
 * agregar una sección es agregar una fila, sin tocar los componentes.
 */
export interface Seccion {
  ruta: string;
  etiqueta: string;
  permiso: Permiso;
}

export const SECCIONES: Seccion[] = [
  { ruta: '/alertas', etiqueta: 'Alertas', permiso: 'ALERTAS:VER' },
  { ruta: '/usuarios', etiqueta: 'Usuarios', permiso: 'USUARIOS:VER' },
  { ruta: '/reportes', etiqueta: 'Reportes', permiso: 'REPORTES:VER' },
  { ruta: '/administracion', etiqueta: 'Administración', permiso: 'ADMINISTRACION:VER' },
];
