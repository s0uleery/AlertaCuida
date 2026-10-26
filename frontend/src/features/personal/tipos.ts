// Formatos de los endpoints de cuentas del personal (PersonalController y ActivacionController).

/** Roles que se pueden asignar a una cuenta nueva. */
export type Rol = 'ADMINISTRADOR' | 'COORDINADOR' | 'PERSONAL';

/** Cuerpo de POST /api/personal. */
export interface SolicitudCuenta {
  /** Con o sin puntos */
  run: string;
  nombre: string;
  /** Opcional */
  cargo: string;
  correo: string;
  rol: Rol;
}

/** Respuesta del alta y de un enlace nuevo. */
export interface RespuestaEnlace {
  idUsuario: string;
  /** Dirección completa, con el token después de "#" */
  enlaceActivacion: string;
  /** Fecha ISO en UTC */
  expiraEn: string;
}
