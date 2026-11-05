/** Permisos como "MODULO:ACCION", igual que en el backend (tabla permiso_rol). */
export type Permiso = `${Modulo}:${Accion}`;
/** Módulos del dashboard. USUARIOS son los pacientes; las cuentas del equipo van en ADMINISTRACION. */
export type Modulo = 'ALERTAS' | 'USUARIOS' | 'REPORTES' | 'ADMINISTRACION';
/** Acciones posibles sobre cada módulo. */
export type Accion = 'VER' | 'CREAR' | 'EDITAR' | 'ELIMINAR' | 'EXPORTAR';

/** Persona del equipo con sesión iniciada, con los permisos de su rol. */
export interface Usuario {
  id: string;
  nombre: string;
  rol: 'ADMINISTRADOR' | 'COORDINADOR' | 'PERSONAL' | string;
  permisos: Permiso[];
}

/** Respuesta de POST /api/auth/login y /api/auth/renovar. */
export interface RespuestaLogin {
  token: string;
  expiraEn: string;
  /** Segundos sin usar el dashboard tras los cuales se cierra la sesión. */
  inactividadSegundos: number;
  usuario: Usuario;
}
