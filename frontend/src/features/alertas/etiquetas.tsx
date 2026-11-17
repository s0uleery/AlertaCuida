import type { EstadoAtencion, EstadoContacto, Severidad, TipoEvento } from './tipos';

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
  return (
    <span className="inline-block rounded-full bg-slate-200 px-2.5 py-0.5 text-xs font-bold text-slate-700">
      Simulacro
    </span>
  );
}

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
