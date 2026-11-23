import { pedir } from '../../api/cliente';

/**
 * Una lectura más allá del límite grave sin alerta del reloj a 30 minutos o menos: un posible evento que el
 * reloj no alertó. No se avisó a nadie; sirve para revisar el reloj.
 */
export interface Hallazgo {
  paciente: { id: string; nombres: string | null; apellidos: string | null };
  variable: 'FC' | 'SPO2';
  /** En latidos por minuto o en % */
  valor: number;
  /** Instante ISO en UTC, en la hora del reloj */
  fechaHoraMedicion: string;
  /** El límite grave vigente al medir que la lectura superó */
  limiteGrave: number;
}

/** Llamadas al backend de la red de seguridad. Exigen REPORTES:VER. */
export const hallazgosApi = {
  /**
   * Hallazgos del rango, el más reciente primero.
   *
   * @param desde primer día (AAAA-MM-DD), incluido
   * @param hasta último día (AAAA-MM-DD), incluido; a lo más 31 días en total
   */
  listar: (desde: string, hasta: string) =>
    pedir<Hallazgo[]>(`/hallazgos?${new URLSearchParams({ desde, hasta }).toString()}`),
};
