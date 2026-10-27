import { CLASE_ERROR, CLASE_TARJETA } from '../../comun/estilos';
import { formatearFecha, hoyEnChile } from '../../comun/fechas';
import { useDatos } from '../../comun/useDatos';
import { calendarioApi } from './calendarioApi';
import { DIAS, type Calendario, type Feriado } from './tipos';

/** Nombre del día de la semana de una fecha "2026-09-18", empezando por el domingo como getDay(). */
const DIA_DE_LA_SEMANA = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];

/** Clases de las celdas de las tablas, como en la portada. */
const CELDA = 'px-3 py-2.5';

/**
 * Calendario hábil, de solo lectura: el horario del PRLAC y los feriados. Explica por qué una
 * alerta leve quedó esperando al día hábil. Lo ve todo el equipo.
 */
export function PaginaCalendario() {
  const { datos, error, cargando } = useDatos(calendarioApi.consultar);

  return (
    <div className="space-y-5">
      {error && <p className={CLASE_ERROR}>{error}</p>}
      {cargando && <p className="text-slate-500">Cargando…</p>}
      {datos && (
        <>
          <HorarioHabil calendario={datos} />
          <Feriados feriados={datos.feriados} />
          <p className="text-sm text-slate-500">
            El horario y los feriados están en la configuración del servidor (calendario.yml). Para cambiarlos hay
            que editarla y reiniciar el backend.
          </p>
        </>
      )}
    </div>
  );
}

/** Tabla con el horario de cada día de la semana; los días sin horario dicen "No hábil". */
function HorarioHabil({ calendario }: { calendario: Calendario }) {
  return (
    <section className={CLASE_TARJETA}>
      <h1 className="text-2xl font-medium text-tinta">Horario hábil</h1>
      <p className="mb-4 text-tinta">
        Una alerta leve que llega fuera de este horario o en un feriado espera al siguiente día hábil. Las alertas
        medias y graves avisan siempre, a los contactos disponibles a esa hora.
      </p>
      <div className="overflow-x-auto rounded-lg border border-prlac">
        <table className="w-full border-collapse text-left text-sm">
          <thead className="bg-prlac text-white">
            <tr>
              <th className={`${CELDA} font-bold`}>Día</th>
              <th className={`${CELDA} font-bold`}>Desde</th>
              <th className={`${CELDA} font-bold`}>Hasta</th>
            </tr>
          </thead>
          <tbody>
            {DIAS.map(({ dia, nombre }) => {
              const tramo = calendario.horarioHabil.find((h) => h.dia === dia);
              return (
                <tr key={dia} className="border-t border-slate-300 odd:bg-slate-50">
                  <td className={CELDA}>{nombre}</td>
                  {tramo ? (
                    <>
                      <td className={CELDA}>{tramo.inicio}</td>
                      <td className={CELDA}>{tramo.fin}</td>
                    </>
                  ) : (
                    <td className={`${CELDA} text-slate-500`} colSpan={2}>
                      No hábil
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <p className="mt-2 text-sm text-slate-500">Hora de Chile. A la hora de término ya es fuera de horario.</p>
    </section>
  );
}

/** Feriados agrupados por año; los que ya pasaron se ven más claros. */
function Feriados({ feriados }: { feriados: Feriado[] }) {
  const hoy = hoyEnChile();
  const anios = [...new Set(feriados.map((f) => f.fecha.slice(0, 4)))];

  return (
    <section className={CLASE_TARJETA}>
      <h1 className="text-2xl font-medium text-tinta">Feriados</h1>
      <p className="mb-4 text-tinta">Feriados nacionales. Ese día completo no es hábil.</p>
      {feriados.length === 0 && <p className="text-slate-500">No hay feriados configurados.</p>}
      <div className="space-y-5">
        {anios.map((anio) => (
          <div key={anio}>
            <h2 className="mb-2 text-base font-bold text-tinta">{anio}</h2>
            <div className="overflow-x-auto rounded-lg border border-prlac">
              <table className="w-full border-collapse text-left text-sm">
                <thead className="bg-prlac text-white">
                  <tr>
                    <th className={`${CELDA} font-bold`}>Fecha</th>
                    <th className={`${CELDA} font-bold`}>Día</th>
                    <th className={`${CELDA} font-bold`}>Feriado</th>
                  </tr>
                </thead>
                <tbody>
                  {feriados
                    .filter((f) => f.fecha.startsWith(anio))
                    .map((f) => (
                      <tr
                        key={f.fecha}
                        className={`border-t border-slate-300 odd:bg-slate-50 ${f.fecha < hoy ? 'text-slate-400' : ''}`}
                      >
                        <td className={CELDA}>{formatearFecha(f.fecha)}</td>
                        <td className={CELDA}>{DIA_DE_LA_SEMANA[new Date(`${f.fecha}T12:00:00Z`).getUTCDay()]}</td>
                        <td className={CELDA}>{f.nombre}</td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
