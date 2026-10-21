/** Permisos como "MODULO:ACCION", igual que en el backend (tabla permiso_rol). */
export type Permiso = `${Modulo}:${Accion}`;
export type Modulo = 'ALERTAS' | 'USUARIOS' | 'REPORTES' | 'ADMINISTRACION';
export type Accion = 'VER' | 'CREAR' | 'EDITAR' | 'ELIMINAR' | 'EXPORTAR';

export interface Usuario {
  id: string;
  nombre: string;
  rol: 'ADMINISTRADOR' | 'COORDINADOR' | 'PERSONAL' | string;
  permisos: Permiso[];
}

export interface RespuestaLogin {
  token: string;
  expiraEn: string;
  usuario: Usuario;
}
