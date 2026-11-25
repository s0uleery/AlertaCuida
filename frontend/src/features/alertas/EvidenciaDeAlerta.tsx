import { formatearFechaHora, formatearFechaHoraExacta } from '../../comun/fechas';
import { DISCREPANCIA } from './etiquetas';
import type { Discrepancia, EvidenciaAlerta, LecturaEvidencia, TipoEvento, UbicacionAlerta } from './tipos';

/** Nombre y unidad de cada tipo de lectura. */
const LECTURA: Record<LecturaEvidencia['tipo'], { nombre: string; unidad: string }> = {
  FC: { nombre: 'Frecuencia cardíaca', unidad: 'lpm' },
  SPO2: { nombre: 'Saturación de oxígeno', unidad: '%' },
};

/** @returns "45 s", "1 min" o "2 min 30 s" */
function duracion(segundos: number): string {
  if (segundos < 60) return `${segundos} s`;
  const minutos = Math.floor(segundos / 60);
  const resto = segundos % 60;
  return resto === 0 ? `${minutos} min` : `${minutos} min ${resto} s`;
}

/** @returns el valor sin decimales innecesarios: 128 o 88,5 */
function numero(valor: number): string {
  return valor.toLocaleString('es-CL', { maximumFractionDigits: 1 });
}

/**
 * Enlace a Google Maps. Solo se abre si alguien hace clic: la ficha no envía la ubicación a ningún
 * servicio externo al mostrarse (decisión del 19-11-2026).
 */
function enlaceMapa(ubicacion: UbicacionAlerta): string {
  return `https://www.google.com/maps?q=${ubicacion.latitud},${ubicacion.longitud}`;
}

/** Datos sueltos de la evidencia: etiqueta y valor, solo los que vienen. */
function datosSueltos(evidencia: EvidenciaAlerta, tipoEvento: TipoEvento): [string, string][] {
  const filas: [string, string][] = [];
  if (evidencia.nivelBateria !== null) filas.push(['Batería', `${evidencia.nivelBateria} %`]);
  if (evidencia.segundosInmovilidad !== null) {
    filas.push(['Sin moverse', duracion(evidencia.segundosInmovilidad)]);
  }
  if (evidencia.alertasEnVentana !== null) {
    const ventana = evidencia.ventanaMinutos !== null ? ` en ${evidencia.ventanaMinutos} minutos` : '';
    filas.push(['Alertas recibidas', `${evidencia.alertasEnVentana}${ventana}`]);
  }
  if (evidencia.noPuestoDesde !== null) filas.push(['No puesto desde', formatearFechaHora(evidencia.noPuestoDesde)]);
  if (evidencia.ultimaSincronizacion !== null) {
    filas.push(['Última sincronización', formatearFechaHora(evidencia.ultimaSincronizacion)]);
  } else if (tipoEvento === 'SILENCIO_DATOS') {
    filas.push(['Última sincronización', 'Nunca sincronizó']);
  }
  return filas;
}

/** Propiedades de EvidenciaDeAlerta. */
interface Props {
  evidencia: EvidenciaAlerta;
  discrepancias: Discrepancia[];
  /** Para saber si la falta de sincronización significa "nunca sincronizó" */
  tipoEvento: TipoEvento;
}

/**
 * Lo que explica la alerta: las lecturas que mandó el reloj con su hora, la batería, la inmovilidad, la
 * ubicación con un enlace al mapa y, en las técnicas del servidor, el dato que la disparó. Debajo, lo que no
 * calzó con la configuración vigente al recibirla.
 */
export function EvidenciaDeAlerta({ evidencia, discrepancias, tipoEvento }: Props) {
  const sueltos = datosSueltos(evidencia, tipoEvento);
  const { lecturas, ubicacion } = evidencia;
  const vacia = lecturas.length === 0 && sueltos.length === 0 && ubicacion === null;

  return (
    <div className="space-y-4">
      {vacia && <p className="text-apagado">Esta alerta no trae evidencia.</p>}

      {lecturas.length > 0 && (
        <div className="overflow-x-auto rounded-lg border border-linea">
          <table className="w-full border-collapse text-left text-sm">
            <thead className="bg-superficie-sub text-texto">
              <tr>
                <th className="px-3 py-2 font-medium">Lectura</th>
                <th className="px-3 py-2 font-medium">Valor</th>
                <th className="px-3 py-2 font-medium">Medida</th>
              </tr>
            </thead>
            <tbody>
              {lecturas.map((lectura, i) => (
                <tr key={`${lectura.fechaHoraMedicion}-${lectura.tipo}-${i}`} className="border-t border-linea">
                  <td className="px-3 py-2">{LECTURA[lectura.tipo].nombre}</td>
                  <td className="px-3 py-2 font-medium tabular-nums">
                    {numero(lectura.valor)} {LECTURA[lectura.tipo].unidad}
                  </td>
                  <td className="px-3 py-2 text-apagado">{formatearFechaHoraExacta(lectura.fechaHoraMedicion)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {sueltos.length > 0 && (
        <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-1 text-sm">
          {sueltos.map(([etiqueta, valor]) => (
            <div key={etiqueta} className="contents">
              <dt className="text-apagado">{etiqueta}</dt>
              <dd className="text-tinta">{valor}</dd>
            </div>
          ))}
        </dl>
      )}

      {ubicacion && (
        <div className="text-sm">
          <p className="text-apagado">Ubicación</p>
          <p className="text-tinta tabular-nums">
            {ubicacion.latitud.toFixed(5)}, {ubicacion.longitud.toFixed(5)}
            {ubicacion.precisionMetros !== null && ` (± ${numero(ubicacion.precisionMetros)} m)`}
          </p>
          <p className="text-xs text-apagado">Medida el {formatearFechaHora(ubicacion.fechaHoraMedicion)}</p>
          <a
            href={enlaceMapa(ubicacion)}
            target="_blank"
            rel="noopener noreferrer"
            className="text-teal-oscuro hover:underline"
          >
            Ver en el mapa ↗
          </a>
        </div>
      )}

      {discrepancias.length > 0 && (
        <div className="rounded-lg border border-naranja-borde bg-naranja-tinte p-3 text-sm">
          <p className="font-medium text-naranja-texto">Revisar: la alerta no calza con la configuración vigente</p>
          <ul className="mt-1 list-disc space-y-1 pl-5 text-tinta">
            {discrepancias.map((d) => (
              <li key={d.codigo}>
                {DISCREPANCIA[d.codigo]}
                {d.detalle && <span className="text-apagado">: {d.detalle}</span>}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
