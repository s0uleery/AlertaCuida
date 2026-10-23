// Formatos de /api/pacientes (PacientesController y VinculoRelojController).
// En la pantalla se les llama "usuarios", como en el PRLAC; en el código y el backend, "pacientes".

export type Sexo = 'FEMENINO' | 'MASCULINO';
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

/** Llave del reloj: se muestra una sola vez para ingresarla en la app del reloj. */
export interface TokenReloj {
  idAsignacion: string;
  token: string;
}
