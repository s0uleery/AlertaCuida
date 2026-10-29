import { Link, Navigate, useParams } from 'react-router';
import { CLASE_ERROR, CLASE_TARJETA } from '../../comun/estilos';
import { useDatos } from '../../comun/useDatos';
import { pacientesApi } from '../pacientes/pacientesApi';
import { PERIODOS } from './periodos';

/**
 * Reporte diario o semanal de un usuario (/reportes/:id/:periodo). Por ahora solo muestra a quién
 * corresponde: el backend todavía no expone el historial de signos vitales ni las alertas.
 */
export function ReporteUsuario() {
  const { id = '', periodo = '' } = useParams();
  const { datos: detalle, error, cargando } = useDatos(() => pacientesApi.detalle(id), id);
  const actual = PERIODOS.find((p) => p.valor === periodo);

  if (!actual) return <Navigate to="/reportes" replace />;
  if (cargando && !detalle) return <p className="text-slate-500">Cargando…</p>;
  if (error || !detalle) return <p className={CLASE_ERROR}>{error ?? 'No se encontró el usuario.'}</p>;

  const { paciente, edad } = detalle;

  return (
    <div className="space-y-6">
      <p className="text-slate-500">
        <Link to="/reportes" className="font-medium text-prlac hover:underline">
          Reportes
        </Link>{' '}
        / Reporte por usuario
      </p>

      <section className={`${CLASE_TARJETA} space-y-4`}>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h1 className="text-xl font-semibold text-slate-900">
            Reporte {actual.etiqueta.toLowerCase()} · {paciente.datos.nombres} {paciente.datos.apellidos}
            {edad !== null && `, ${edad}`}
          </h1>
          <Link to={`/usuarios/${paciente.id}`} className="font-medium text-sky-700 hover:underline">
            Detalle del usuario
          </Link>
        </div>

        <div className="flex max-w-sm">
          {PERIODOS.map(({ valor, etiqueta }) => (
            <Link
              key={valor}
              to={`/reportes/${paciente.id}/${valor}`}
              className={`flex-1 border px-3 py-2 text-center text-sm font-medium ${
                valor === actual.valor
                  ? 'border-prlac bg-prlac text-white'
                  : 'border-slate-300 text-slate-500 hover:text-slate-700'
              }`}
            >
              {etiqueta}
            </Link>
          ))}
        </div>

        <p className="text-slate-500">
          Todavía no hay datos para este reporte: el backend aún no entrega el historial de signos vitales ni las
          alertas del usuario.
        </p>
      </section>
    </div>
  );
}
