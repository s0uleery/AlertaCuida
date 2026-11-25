import { Insignia, type TonoInsignia } from '../../comun/ui/Insignia';
import type {
  Canal,
  ClasificacionAlerta,
  CodigoDiscrepancia,
  EstadoAtencion,
  EstadoContacto,
  ResumenAlerta,
  Severidad,
  TipoEvento,
} from './tipos';

/** Cómo se muestra cada tipo de evento (la "condición médica" del mockup). */
export const CONDICION: Record<TipoEvento, string> = {
  CAIDA: 'Caída detectada',
  FC: 'Frecuencia cardíaca fuera de rango',
  SPO2: 'Saturación de oxígeno baja',
  FC_SPO2: 'Frecuencia cardíaca y saturación fuera de rango',
  TENDENCIA: 'Tendencia de signos vitales',
  INACTIVIDAD: 'Inactividad prolongada',
  ZONA_SEGURA: 'Salió de su zona segura',
  BOTON_AYUDA: 'Pidió ayuda con el botón del reloj',
  SILENCIO_DATOS: 'Reloj sin enviar datos',
  BATERIA_BAJA: 'Batería baja del reloj',
  RELOJ_NO_PUESTO: 'Reloj no puesto',
  EXCESO_ALERTAS: 'Reloj con demasiadas alertas (revisar el reloj)',
};

/** Cómo se muestra cada estado de atención. */
export const ESTADO: Record<EstadoAtencion, string> = {
  SIN_ASIGNAR: 'Nueva',
  EN_ATENCION: 'En atención',
  EN_SEGUIMIENTO: 'En seguimiento',
  RESUELTA: 'Cerrada',
};

/** Cómo se muestra el recorrido de avisos del reloj. AVISANDO_CONTACTO se muestra con el nombre del contacto. */
export const ESTADO_CONTACTO: Record<EstadoContacto, string> = {
  RECIBIDA: 'Recibida',
  AVISANDO_CONTACTO: 'Avisando',
  CONFIRMADA: 'Un contacto va en camino',
  SIN_RESPUESTA: 'Nadie respondió',
  RESUELTA: 'Cerrada',
  PENDIENTE_DIA_HABIL: 'Espera el día hábil',
  CANCELADA: 'Cancelada por el usuario',
};

/** Etiqueta gris de los simulacros, para no confundirlos con una emergencia real. */
export function InsigniaSimulacro() {
  return <Insignia tono="neutra">Simulacro</Insignia>;
}

/** Texto y tono de cada gravedad. */
export const GRAVEDAD: Record<Severidad, { texto: string; tono: TonoInsignia }> = {
  GRAVE: { texto: 'Grave', tono: 'grave' },
  MEDIA: { texto: 'Media', tono: 'media' },
  LEVE: { texto: 'Leve', tono: 'leve' },
};

/** Insignia de color con la gravedad, con su punto (magenta, naranja o verde), como en el mockup. */
export function InsigniaGravedad({ severidad }: { severidad: Severidad }) {
  const { texto, tono } = GRAVEDAD[severidad];
  return <Insignia tono={tono}>{texto}</Insignia>;
}

/** Tono de la insignia de cada estado de atención: lo nuevo resalta y lo cerrado va en verde. */
const TONO_ESTADO: Record<EstadoAtencion, TonoInsignia> = {
  SIN_ASIGNAR: 'nueva',
  EN_ATENCION: 'neutra',
  EN_SEGUIMIENTO: 'teal',
  RESUELTA: 'cerrada',
};

/** Insignia del estado de atención de una alerta. */
export function InsigniaEstado({ estado }: { estado: EstadoAtencion }) {
  return <Insignia tono={TONO_ESTADO[estado]}>{ESTADO[estado]}</Insignia>;
}

/** Orden de urgencia: grave primero. */
export const PESO_GRAVEDAD: Record<Severidad, number> = { GRAVE: 0, MEDIA: 1, LEVE: 2 };

/** Cómo se muestra la clasificación con que se cerró una alerta de salud. */
export const CLASIFICACION: Record<ClasificacionAlerta, string> = {
  POSITIVA: 'Positiva (emergencia real)',
  FALSA_ALARMA: 'Falsa alarma',
};

/** Cómo se muestra cada canal de aviso. */
export const CANAL: Record<Canal, string> = {
  WHATSAPP: 'WhatsApp',
  CORREO: 'Correo',
};

/** Cómo se muestra cada discrepancia con la configuración vigente. */
export const DISCREPANCIA: Record<CodigoDiscrepancia, string> = {
  VERSION_DISTINTA: 'El reloj usaba otra configuración',
  MODULO_APAGADO: 'Llegó de un módulo apagado',
  GRAVEDAD_DISTINTA: 'Gravedad distinta de la configurada',
  LECTURAS_EN_RANGO: 'Las lecturas no superan los umbrales',
};

/** A quién está avisando el reloj, con el nombre del contacto cuando lo hay. */
export function describirContacto(alerta: Pick<ResumenAlerta, 'estadoContacto' | 'contactoEnCurso'>): string {
  if (alerta.estadoContacto === 'AVISANDO_CONTACTO' && alerta.contactoEnCurso) {
    return `Avisando a ${alerta.contactoEnCurso}`;
  }
  return ESTADO_CONTACTO[alerta.estadoContacto];
}
