// Formatos de los endpoints de la sección Administración: cuentas del personal (PersonalController y
// ActivacionController), roles y auditoría.

import type { Permiso } from '../sesion/tipos';

/** Roles de la tabla rol (V6__catalogos_iniciales.sql). */
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
  /** Celular con WhatsApp; obligatorio para COORDINADOR, que recibe los avisos de la oficina PRLAC */
  telefono: string;
  /** La persona aceptó recibir esos avisos por WhatsApp (opt-in que exige Meta) */
  aceptaWhatsapp: boolean;
}

/** Respuesta del alta y de un enlace nuevo. */
export interface RespuestaEnlace {
  idUsuario: string;
  /** Dirección completa, con el token después de "#" */
  enlaceActivacion: string;
  /** Fecha ISO en UTC */
  expiraEn: string;
}

/** ACTIVA: ya eligió su contraseña. PENDIENTE: no ha usado su enlace. DESACTIVADA: tenía contraseña y se le quitó el acceso. */
export type EstadoCuenta = 'ACTIVA' | 'PENDIENTE' | 'DESACTIVADA';

/** Fila de GET /api/personal: una cuenta del equipo. */
export interface PerfilEquipo {
  id: string;
  nombre: string;
  cargo: string | null;
  correo: string;
  rol: Rol;
  estado: EstadoCuenta;
  /** Último LOGIN en la auditoría, fecha ISO en UTC; null si nunca ha entrado */
  ultimoAcceso: string | null;
}

/** Fila de GET /api/roles: un rol con los permisos que tiene hoy. */
export interface RolConPermisos {
  codigo: Rol;
  nombre: string;
  /** El administrador: sus permisos no se editan */
  esSistema: boolean;
  permisos: Permiso[];
}

/** Valores de registro_auditoria.accion. */
export type AccionAuditoria =
  | 'LEER'
  | 'CREAR'
  | 'MODIFICAR'
  | 'ELIMINAR'
  | 'EXPORTAR'
  | 'LOGIN'
  | 'LOGIN_FALLIDO'
  | 'TOKEN_REUTILIZADO';

/** Valores de registro_auditoria.entidad. */
export type EntidadAuditoria =
  | 'PACIENTE'
  | 'FICHA_PACIENTE'
  | 'CONDICION_PACIENTE'
  | 'CONTACTO'
  | 'CONTACTO_PACIENTE'
  | 'CONFIGURACION'
  | 'UMBRAL'
  | 'ZONA_SEGURA'
  | 'DOCUMENTO'
  | 'SMARTWATCH'
  | 'ASIGNACION_RELOJ'
  | 'ALERTA'
  | 'ATENCION_ALERTA'
  | 'SEGUIMIENTO'
  | 'USUARIO'
  | 'ROL'
  | 'PERMISO_ROL'
  | 'REPORTE';

/** Fila de GET /api/auditoria. No trae datos personales del objeto, solo su identificador. */
export interface RegistroAuditoria {
  id: string;
  /** Fecha ISO en UTC */
  fechaHora: string;
  accion: AccionAuditoria;
  /** null en LOGIN, LOGIN_FALLIDO y TOKEN_REUTILIZADO */
  entidad: EntidadAuditoria | null;
  /** En LEER es el id del paciente o "LISTA"; en las demás acciones, el id del objeto cambiado */
  idEntidad: string | null;
  /** Ej. valor anterior y nuevo */
  detalle: Record<string, unknown> | null;
  /** Quién lo hizo; null en un login fallido con un RUN que no existe o en un cambio del sistema */
  usuario: { id: string; nombre: string } | null;
  ip: string | null;
}
