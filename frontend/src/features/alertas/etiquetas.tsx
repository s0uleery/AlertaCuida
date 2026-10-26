import type { EstadoAtencion, Severidad, TipoEvento } from './tipos';

/** Cómo se muestra cada tipo de evento (la "condición médica" del mockup). */
export const CONDICION: Record<TipoEvento, string> = {
  CAIDA: 'Caída detectada',
  FC: 'Frecuencia cardíaca fuera de rango',
  SPO2: 'Saturación de oxígeno baja',
  FC_SPO2: 'Frecuencia cardíaca y saturación fuera de rango',
  TENDENCIA: 'Tendencia de signos vitales',
  INACTIVIDAD: 'Inactividad prolongada',
  SILENCIO_DATOS: 'Reloj sin enviar datos',
  BATERIA_BAJA: 'Batería baja del reloj',
  RELOJ_NO_PUESTO: 'Reloj no puesto',
};

/** Cómo se muestra cada estado de atención. */
export const ESTADO: Record<EstadoAtencion, string> = {
  SIN_ASIGNAR: 'Nueva',
  EN_ATENCION: 'En atención',
  EN_SEGUIMIENTO: 'En seguimiento',
  RESUELTA: 'Cerrada',
};

const GRAVEDAD: Record<Severidad, { texto: string; clase: string }> = {
  GRAVE: { texto: 'Grave', clase: 'bg-[#fde3e3] text-[#a1111a]' },
  MEDIA: { texto: 'Media', clase: 'bg-[#fff0cc] text-[#7a4e00]' },
  LEVE: { texto: 'Leve', clase: 'bg-[#dff2de] text-[#14540f]' },
};

/** Insignia de color con la gravedad. */
export function InsigniaGravedad({ severidad }: { severidad: Severidad }) {
  const { texto, clase } = GRAVEDAD[severidad];
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-bold ${clase}`}>{texto}</span>;
}
