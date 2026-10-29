import type { ComponentType } from 'react';
import { PaginaCalendario } from '../features/calendario/PaginaCalendario';
import { PaginaUsuarios } from '../features/pacientes/PaginaUsuarios';
import { PaginaAdministracion } from '../features/personal/PaginaAdministracion';
import { PaginaRelojes } from '../features/relojes/PaginaRelojes';
import { PaginaReportes } from '../features/reportes/PaginaReportes';
import type { Permiso } from '../features/sesion/tipos';

/**
 * Secciones del dashboard y el permiso que exige cada una. El menú y las rutas protegidas salen de aquí:
 * agregar una sección es agregar una fila, sin tocar los componentes.
 */
export interface Seccion {
  ruta: string;
  etiqueta: string;
  /** Sin permiso, la ve cualquiera con sesión */
  permiso?: Permiso;
  /** Página de la sección; puede tener sus propias rutas internas. Sin ella se muestra "todavía no está construida" */
  pagina?: ComponentType;
}

/** Secciones en el orden en que aparecen en el menú. */
export const SECCIONES: Seccion[] = [
  { ruta: '/alertas', etiqueta: 'Alertas', permiso: 'ALERTAS:VER' },
  // "Usuarios" son los usuarios del programa (pacientes), como se les llama en el PRLAC
  { ruta: '/usuarios', etiqueta: 'Usuarios', permiso: 'USUARIOS:VER', pagina: PaginaUsuarios },
  // Los relojes usan los permisos de USUARIOS: quien inscribe al usuario le entrega el reloj
  { ruta: '/relojes', etiqueta: 'Relojes', permiso: 'USUARIOS:VER', pagina: PaginaRelojes },
   { ruta: '/reportes', etiqueta: 'Reportes', permiso: 'REPORTES:VER' },
  // Horario hábil y feriados: lo ve todo el equipo, para entender por qué una alerta leve espera
  { ruta: '/calendario', etiqueta: 'Calendario', pagina: PaginaCalendario },
  // Aquí van las cuentas del equipo
  { ruta: '/administracion', etiqueta: 'Administración', permiso: 'ADMINISTRACION:VER', pagina: PaginaAdministracion },
];
