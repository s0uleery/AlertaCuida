import { formatearFechaHora } from '../../comun/fechas';
import { useDatos } from '../../comun/useDatos';
import { pacientesApi } from './pacientesApi';

/** Propiedades de ConfiguracionDelReloj. */
interface Props {
  idPaciente: string;
  /** Vínculo vigente: al reemplazar el reloj se vuelve a consultar */
  idAsignacion: string;
}

/**
 * Si el reloj ya aplica la configuración vigente: así el equipo sabe si un cambio de contactos,
 * umbrales o calendario ya llegó al reloj. El reloj la descarga en su próxima sincronización.
 */
export function ConfiguracionDelReloj({ idPaciente, idAsignacion }: Props) {
  const { datos, error } = useDatos(() => pacientesApi.configuracionReloj(idPaciente), idAsignacion);

  if (error) return <p className="text-sm text-slate-500">No se pudo saber si el reloj tiene la configuración.</p>;
  if (!datos || datos.estado === 'SIN_RELOJ') return null;

  if (datos.estado === 'SIN_SINCRONIZAR') {
    return (
      <p className="text-sm text-slate-600">
        <span className="mr-2 rounded-full bg-slate-200 px-2 py-0.5 text-xs text-slate-600">Sin sincronizar</span>
        El reloj aún no envía datos; descargará su configuración en la primera sincronización.
      </p>
    );
  }

  if (datos.estado === 'AL_DIA') {
    return (
      <p className="text-sm text-slate-600">
        <span className="mr-2 rounded-full bg-emerald-100 px-2 py-0.5 text-xs text-emerald-800">
          Configuración al día
        </span>
        La aplica desde el {formatearFechaHora(datos.aplicadaDesde!)}.
      </p>
    );
  }

  return (
    <p className="text-sm text-slate-600">
      <span className="mr-2 rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-800">Configuración pendiente</span>
      El reloj aún no descarga los últimos cambios; lo hará en su próxima sincronización (cada 2 horas). Última
      sincronización: {formatearFechaHora(datos.ultimaSincronizacion!)}.
    </p>
  );
}
