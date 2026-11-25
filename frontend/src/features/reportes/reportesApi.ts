import { descargar, pedir } from '../../api/cliente';
import type { Severidad, TipoEvento } from '../alertas/tipos';
import type { Periodo } from './periodos';

/** Promedio, mínimo y máximo de una variable en el período; promedio null si no hubo lecturas. */
export interface Estadistica {
  promedio: number | null;
  minimo: number | null;
  maximo: number | null;
  lecturas: number;
}

/** Indicadores de un período (GET /api/pacientes/{id}/reportes/diario|semanal). */
export interface IndicadoresPeriodo {
  fc: Estadistica;
  spo2: Estadistica;
  uso: { minutosPuesto: number; sincronizaciones: number };
  /** null si el reloj no mandó batería */
  bateriaMinima: number | null;
  alertasPorTipo: Partial<Record<TipoEvento, number>>;
  alertasPorGravedad: Partial<Record<Severidad, number>>;
  alertasCanceladas: number;
}

/** Reporte de un usuario; el semanal trae la semana anterior para comparar. */
export interface ReporteUsuario {
  periodo: 'DIARIO' | 'SEMANAL';
  /** "2026-11-23" */
  desde: string;
  hasta: string;
  indicadores: IndicadoresPeriodo;
  anterior: IndicadoresPeriodo | null;
}

/** Un punto de la curva: lecturas juntas por lectura, hora o día. */
export interface PuntoTendencia {
  fechaHora: string;
  promedio: number;
  minimo: number;
  maximo: number;
  cantidad: number;
}

/** Una alerta del período, para marcarla en la curva y listarla. */
export interface MarcaAlerta {
  id: string;
  numero: number;
  tipoEvento: TipoEvento;
  severidad: Severidad;
  fechaHoraDeteccion: string;
  cancelada: boolean;
}

/** Evolución de FC y SpO2 (GET /api/pacientes/{id}/tendencias). */
export interface Tendencias {
  agrupacion: 'LECTURA' | 'HORA' | 'DIA';
  desde: string;
  hasta: string;
  fc: PuntoTendencia[];
  spo2: PuntoTendencia[];
  alertas: MarcaAlerta[];
}

/** Formato de exportación. */
export type Formato = 'xlsx' | 'pdf';

/** Llamadas al backend de Reportería. Ver exige REPORTES:VER; exportar, REPORTES:EXPORTAR. */
export const reportesApi = {
  /** Reporte diario o semanal; dia = cualquier día del período ("2026-11-25"). */
  reporte: (idPaciente: string, periodo: Periodo, dia: string) =>
    pedir<ReporteUsuario>(`/pacientes/${idPaciente}/reportes/${periodo}?dia=${dia}`),

  /** Curvas de FC y SpO2 con las alertas, entre dos días incluidos. */
  tendencias: (idPaciente: string, desde: string, hasta: string) =>
    pedir<Tendencias>(`/pacientes/${idPaciente}/tendencias?desde=${desde}&hasta=${hasta}`),

  /** Descarga el reporte del usuario. */
  exportarUsuario: (idPaciente: string, periodo: Periodo, dia: string, formato: Formato) =>
    descargar(`/pacientes/${idPaciente}/reportes/${periodo}.${formato}?dia=${dia}`, `reporte-${periodo}.${formato}`),

  /** Descarga la gestión operativa del período. */
  exportarGestion: (desde: string, hasta: string, formato: Formato) =>
    descargar(`/reportes/gestion.${formato}?desde=${desde}&hasta=${hasta}`, `gestion.${formato}`),
};
