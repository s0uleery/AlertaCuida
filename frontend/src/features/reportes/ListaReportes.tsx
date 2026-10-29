import { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { CLASE_CAMPO, CLASE_ERROR, CLASE_TARJETA } from '../../comun/estilos';
import { useDatos } from '../../comun/useDatos';
import { coincide } from '../pacientes/busqueda';
import { pacientesApi } from '../pacientes/pacientesApi';
import { PERIODOS } from './periodos';

type Pestana = 'usuarios' | 'gestion';

/** Lista de reportes con sus dos pestañas: por usuario y gestión de alertas. */
export function ListaReportes() {
  const [pestana, setPestana] = useState<Pestana>('usuarios');

  return (
    <section className={CLASE_TARJETA}>
      <h1 className="text-2xl font-medium text-tinta">Reportes</h1>
      <p className="mb-4 text-tinta">
        Revisa la evolución de cada persona usuaria y cómo se han gestionado las alertas.
      </p>

      <div className="mb-4 flex border-b border-slate-300" role="tablist">
        {(
          [
            ['usuarios', 'Reporte por usuario'],
            ['gestion', 'Gestión de alertas'],
          ] as const
        ).map(([valor, etiqueta]) => (
          <button
            key={valor}
            role="tab"
            aria-selected={pestana === valor}
            onClick={() => setPestana(valor)}
            className={`-mb-px border-b-3 px-4 py-2.5 font-medium ${
              pestana === valor ? 'border-prlac text-prlac' : 'border-transparent text-slate-500 hover:text-slate-700'
            }`}
          >
            {etiqueta}
          </button>
        ))}
      </div>

      {pestana === 'usuarios' ? (
        <ReportesPorUsuario />
      ) : (
        <p className="text-slate-500">Esta pestaña todavía no está construida.</p>
      )}
    </section>
  );
}

/**
 * Usuarios del programa con acceso a su reporte diario y semanal. Usa la misma lista y la misma
 * búsqueda que la sección Usuarios: quien se registra allí aparece aquí.
 */
function ReportesPorUsuario() {
  const [incluirBajas, setIncluirBajas] = useState(false);
  const [busqueda, setBusqueda] = useState('');
  const { datos, error, cargando } = useDatos(() => pacientesApi.listar(incluirBajas), String(incluirBajas));

  const visibles = useMemo(
    () => (datos ?? []).filter((fila) => !busqueda.trim() || coincide(fila, busqueda.trim())),
    [datos, busqueda],
  );

  return (
    <div className="space-y-4">
      <p className="text-sm text-slate-500">
        Evolución de signos vitales, alertas y uso del reloj de cada persona usuaria.
      </p>

      <div className="flex flex-wrap items-center gap-4">
        <input
          value={busqueda}
          onChange={(e) => setBusqueda(e.target.value)}
          placeholder="Buscar por nombre, alias o RUN"
          aria-label="Buscar"
          className={`${CLASE_CAMPO} mt-0 max-w-sm`}
        />
        <label className="flex items-center gap-2 text-sm text-slate-600">
          <input type="checkbox" checked={incluirBajas} onChange={(e) => setIncluirBajas(e.target.checked)} />
          Mostrar dados de baja
        </label>
      </div>

      {error && <p className={CLASE_ERROR}>{error}</p>}

      <div className="overflow-x-auto rounded-lg border border-slate-200">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Nombre</th>
              <th className="px-4 py-3 font-medium">RUN</th>
              <th className="px-4 py-3 font-medium">Edad</th>
              <th className="px-4 py-3 font-medium">Ver reporte</th>
            </tr>
          </thead>
          <tbody>
            {visibles.map(({ paciente, edad }) => (
              <tr key={paciente.id} className="border-b border-slate-100 last:border-0">
                <td className="px-4 py-3">
                  <Link to={`/usuarios/${paciente.id}`} className="font-medium text-sky-700 hover:underline">
                    {paciente.datos.apellidos}, {paciente.datos.nombres}
                  </Link>
                  {paciente.fechaBaja && (
                    <span className="ml-2 rounded-full bg-slate-200 px-2 py-0.5 text-xs text-slate-600">De baja</span>
                  )}
                </td>
                <td className="px-4 py-3 font-mono">{paciente.datos.run}</td>
                <td className="px-4 py-3">{edad ?? '—'}</td>
                <td className="px-4 py-3">
                  <div className="flex gap-2">
                    {PERIODOS.map(({ valor, etiqueta }) => (
                      <Link
                        key={valor}
                        to={`/reportes/${paciente.id}/${valor}`}
                        className="rounded-full bg-slate-100 px-3.5 py-1 text-xs font-bold text-prlac hover:bg-slate-200"
                      >
                        {etiqueta}
                      </Link>
                    ))}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {cargando && <p className="px-4 py-3 text-slate-500">Cargando…</p>}
        {!cargando && visibles.length === 0 && (
          <p className="px-4 py-3 text-slate-500">
            {busqueda ? 'Nadie coincide con la búsqueda.' : 'Todavía no hay usuarios registrados.'}
          </p>
        )}
      </div>
    </div>
  );
}
