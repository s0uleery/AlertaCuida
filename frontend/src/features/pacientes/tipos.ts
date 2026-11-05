// Formatos de /api/pacientes (PacientesController y VinculoRelojController).
// En la pantalla se les llama "usuarios", como en el PRLAC; en el código y el backend, "pacientes".

export type Sexo = 'FEMENINO' | 'MASCULINO';
/** Nivel de dependencia evaluado por el PRLAC. */
export type NivelDependencia = 'MODERADA' | 'SEVERA';

/** Identidad del paciente, como se escribe y como se guarda. Las fechas van como "2026-10-24". */
export interface DatosPaciente {
  /** Con o sin puntos al enviar; el backend lo devuelve sin puntos */
  run: string;
  nombres: string;
  apellidos: string;
  /** Nombre corto para los mensajes a los contactos, sin datos clínicos */
  alias: string | null;
  fechaNacimiento: string | null;
  sexo: Sexo | null;
  direccion: string | null;
  /** null = por evaluar */
  nivelDependencia: NivelDependencia | null;
  /** Sin ella no se le puede vincular un reloj */
  fechaConsentimiento: string | null;
  fechaIngreso: string;
}

/** Un paciente guardado. */
export interface Paciente {
  id: string;
  datos: DatosPaciente;
  /** null mientras sigue en el programa */
  fechaBaja: string | null;
}

/** Fila de la lista. */
export interface ResumenPaciente {
  paciente: Paciente;
  /** Años cumplidos; null sin fecha de nacimiento */
  edad: number | null;
  /** Identificador del reloj vinculado, o null */
  relojActual: string | null;
}

/** Un período con un reloj. */
export interface AsignacionHistorial {
  idAsignacion: string;
  idDispositivo: string;
  identificadorReloj: string;
  /** Instantes ISO en UTC */
  inicio: string;
  /** null = vigente */
  fin: string | null;
}

/** Ficha con historial de relojes (el más reciente primero). */
export interface DetallePaciente {
  paciente: Paciente;
  edad: number | null;
  historial: AsignacionHistorial[];
}

/** Si el reloj ya aplica la configuración vigente, según su última sincronización. */
export type EstadoConfiguracion = 'AL_DIA' | 'PENDIENTE' | 'SIN_SINCRONIZAR' | 'SIN_RELOJ';

/** Qué configuración aplica el reloj del usuario. */
export interface ConfiguracionAplicada {
  estado: EstadoConfiguracion;
  /** La que calcula hoy el servidor */
  versionVigente: string;
  /** La que informó el reloj en su último lote; null si no ha informado ninguna */
  versionReloj: string | null;
  /** Instante ISO desde el que el reloj informa esa versión; null si no ha sincronizado */
  aplicadaDesde: string | null;
  /** Instante ISO de su último lote; null si no ha sincronizado */
  ultimaSincronizacion: string | null;
}

/** Llave del reloj: se muestra una sola vez para ingresarla en la app del reloj. */
export interface TokenReloj {
  idAsignacion: string;
  token: string;
}

/** Enfermedad (EPOC, hipertensión…) o situación (movilidad reducida, vive sola…). */
export type TipoCondicion = 'ENFERMEDAD' | 'SITUACION';

/** Una enfermedad o situación del usuario (CondicionesController). */
export interface Condicion {
  id: string;
  tipo: TipoCondicion;
  /** Texto libre, sin espacios sobrantes */
  descripcion: string;
}

/** Qué vigila el reloj en el usuario. */
export interface Modulos {
  caidas: boolean;
  fc: boolean;
  spo2: boolean;
  inactividad: boolean;
  tendencia: boolean;
  /** Alerta al salir de la zona segura; exige GPS y una zona definida */
  zonaSegura: boolean;
  /** Botón del reloj para pedir ayuda: alerta leve que avisa primero a los vecinos */
  botonAyuda: boolean;
}

/** Módulos y opciones del usuario (una fila del historial de configuración). */
export interface AjustesClinicos {
  modulos: Modulos;
  /** Ubica a la persona y, con el módulo zonaSegura, revisa la zona */
  gpsActivo: boolean;
  modoConservador: boolean;
  /** Antes de pasar al siguiente contacto; null = valor base */
  minutosEspera: number | null;
  /** Por qué se cambió (opcional) */
  motivo: string | null;
}

/** Límites de una variable: graveBajo < normalMin < normalMax < graveAlto. La SpO2 no tiene límites altos. */
export interface Umbral {
  graveBajo: number;
  normalMin: number;
  normalMax: number | null;
  graveAlto: number | null;
}

/** El umbral que rige para una variable: propio del usuario o el base. */
export interface UmbralVigente {
  umbral: Umbral;
  /** false = rige el umbral base */
  propio: boolean;
  /** Instante ISO desde el que rige el ajuste, o null */
  desde: string | null;
}

/** Lo que rige hoy para el usuario (PerfilClinicoController). */
export interface PerfilClinico {
  ajustes: AjustesClinicos;
  /** Instante ISO del último cambio; null si nunca se configuró */
  ajustesDesde: string | null;
  /** Los que rigen: los del usuario o el valor base */
  minutosEspera: number;
  fc: UmbralVigente;
  spo2: UmbralVigente;
  /** La zona guardada (aunque el módulo esté apagado); null si nunca se definió */
  zonaSegura: { zona: ZonaSegura; vigenteDesde: string } | null;
}

/** Gravedad de una alerta. */
export type SeveridadZona = 'LEVE' | 'MEDIA' | 'GRAVE';

/** Círculo de la zona segura: al salir, el reloj envía una alerta con esta gravedad. */
export interface ZonaSegura {
  /** Grados, -90 a 90 */
  latitud: number;
  /** Grados, -180 a 180 */
  longitud: number;
  /** De 50 a 5000 metros */
  radioMetros: number;
  severidad: SeveridadZona;
}

/** Variable de un umbral. */
export type VariableUmbral = 'FC' | 'SPO2';

/** Quién es el contacto respecto del usuario (familia, vecino…). */
export interface TipoContacto {
  codigo: string;
  nombre: string;
}

/** Medio por el que el servidor avisa a un contacto. */
export type Canal = 'WHATSAPP' | 'CORREO';

/** Un contacto de la lista del usuario (ContactosController). */
export interface Contacto {
  /** Identificador del vínculo con el usuario */
  id: string;
  nombre: string;
  /** Normalizado (+569…), o null */
  whatsapp: string | null;
  correo: string | null;
  /** Si se le puede enviar WhatsApp */
  optInVigente: boolean;
  fechaOptIn: string | null;
  fechaRevocacion: string | null;
  tipo: string;
  parentesco: string | null;
  cuidadorPrincipal: boolean;
  /** 1 = el primero al que avisa el reloj */
  prioridad: number;
  /** "HH:mm"; null = todo el día */
  horaInicio: string | null;
  horaFin: string | null;
  activo: boolean;
  /** De cuántos otros usuarios es contacto la misma persona */
  otrosPacientes: number;
  /** Los que el servidor puede usar hoy */
  canales: Canal[];
}

/** Tramo del día ("HH:mm"); inicio igual a fin = el día completo. */
export interface Tramo {
  inicio: string;
  fin: string;
}

/** Lista de contactos con las horas en que nadie puede recibir avisos. */
export interface ListaContactos {
  contactos: Contacto[];
  sinCobertura: Tramo[];
}

/** Lo que se ingresa al agregar o editar un contacto. */
export interface DatosContacto {
  nombre: string;
  whatsapp: string | null;
  correo: string | null;
  tipo: string;
  parentesco: string | null;
  cuidadorPrincipal: boolean;
  horaInicio: string | null;
  horaFin: string | null;
}
