import { formatearAntiguedad } from '../../comun/fechas';
import {
  MOTIVO_SEMAFORO,
  NOMBRE_CONEXION,
  NOMBRE_SEMAFORO,
  type ColorSemaforo,
  type EstadoTecnicoReloj,
} from './tipos';

/** Color de cada círculo. */
const CIRCULO: Record<ColorSemaforo, string> = {
  VERDE: 'bg-emerald-500',
  AMARILLO: 'bg-amber-400',
  ROJO: 'bg-red-600',
  GRIS: 'bg-slate-400',
};

/** @return los motivos en texto, uno por línea, para mostrar al pasar el mouse */
function explicacion(estado: EstadoTecnicoReloj): string {
  if (estado.motivos.length === 0) return 'Todo en orden';
  return estado.motivos.map((motivo) => MOTIVO_SEMAFORO[motivo]).join('\n');
}

/**
 * Círculo de color con el estado del reloj y, al pasar el mouse, por qué. Con `detalle` agrega la última
 * sincronización, la batería, la conexión y los motivos. Sin estado (aún cargando o sin respuesta) muestra un guion.
 */
export function SemaforoReloj({
  estado,
  detalle = false,
}: {
  estado: EstadoTecnicoReloj | undefined;
  detalle?: boolean;
}) {
  if (!estado) return <span className="text-slate-400">—</span>;
  const texto = explicacion(estado);
  return (
    <span className="inline-flex flex-col gap-0.5">
      <span className="inline-flex items-center gap-2" title={texto}>
        <span aria-hidden="true" className={`inline-block h-3 w-3 rounded-full ${CIRCULO[estado.semaforo]}`} />
        <span className="font-medium">{NOMBRE_SEMAFORO[estado.semaforo]}</span>
        <span className="sr-only">: {texto.replaceAll('\n', '; ')}</span>
      </span>
      {detalle && <DetalleTecnico estado={estado} />}
      {!detalle && estado.motivos.length > 0 && estado.semaforo !== 'GRIS' && (
        <span className="text-xs text-slate-500">{MOTIVO_SEMAFORO[estado.motivos[0]]}</span>
      )}
    </span>
  );
}

/** Última sincronización, batería, conexión, versión y motivos, para la ficha del usuario. */
function DetalleTecnico({ estado }: { estado: EstadoTecnicoReloj }) {
  const partes = [
    estado.ultimaSincronizacion
      ? `Sincronizó ${formatearAntiguedad(estado.ultimaSincronizacion)}`
      : 'Todavía no sincroniza',
    estado.nivelBateria !== null ? `batería ${estado.nivelBateria} %` : null,
    estado.tipoConexion ? NOMBRE_CONEXION[estado.tipoConexion] : null,
    estado.versionApp ? `app ${estado.versionApp}` : null,
  ].filter((parte): parte is string => parte !== null);
  return (
    <>
      <span className="text-sm text-slate-600">{partes.join(' · ')}</span>
      {estado.motivos.length > 0 && (
        <ul className="list-disc pl-5 text-sm text-slate-700">
          {estado.motivos.map((motivo) => (
            <li key={motivo}>{MOTIVO_SEMAFORO[motivo]}</li>
          ))}
        </ul>
      )}
    </>
  );
}
