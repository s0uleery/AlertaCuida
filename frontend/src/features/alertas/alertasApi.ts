import type { ResumenAlerta } from './tipos';

/** Instante ISO de hace `minutos` minutos, para que los ejemplos siempre parezcan recientes. */
function haceMinutos(minutos: number): string {
  return new Date(Date.now() - minutos * 60_000).toISOString();
}

// TODO: el backend todavía no expone /api/alertas. Mientras tanto se usan estos ejemplos,
// con el mismo formato que deberá devolver el endpoint.
const EJEMPLOS: ResumenAlerta[] = [
  {
    id: 'ejemplo-142',
    numero: 142,
    paciente: { id: 'ejemplo-p1', nombres: 'María', apellidos: 'Soto Rojas', edad: 82 },
    tipoEvento: 'CAIDA',
    severidad: 'GRAVE',
    estadoAtencion: 'EN_ATENCION',
    responsable: 'Camila Vera',
    recepcion: haceMinutos(25),
  },
  {
    id: 'ejemplo-143',
    numero: 143,
    paciente: { id: 'ejemplo-p2', nombres: 'Juan', apellidos: 'Pérez Díaz', edad: 77 },
    tipoEvento: 'FC',
    severidad: 'MEDIA',
    estadoAtencion: 'SIN_ASIGNAR',
    responsable: null,
    recepcion: haceMinutos(10),
  },
  {
    id: 'ejemplo-141',
    numero: 141,
    paciente: { id: 'ejemplo-p3', nombres: 'Rosa', apellidos: 'Araya Núñez', edad: 85 },
    tipoEvento: 'BATERIA_BAJA',
    severidad: 'LEVE',
    estadoAtencion: 'RESUELTA',
    responsable: 'Diego Muñoz',
    recepcion: haceMinutos(95),
  },
];

/** Llamadas al backend para alertas. Exigen ALERTAS:VER. */
export const alertasApi = {
  /** Alertas más recientes primero. */
  recientes: (): Promise<ResumenAlerta[]> =>
    Promise.resolve([...EJEMPLOS].sort((a, b) => b.recepcion.localeCompare(a.recepcion))),
};
