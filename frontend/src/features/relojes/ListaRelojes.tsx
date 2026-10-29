import { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { CLASE_BOTON_PRINCIPAL, CLASE_CAMPO, CLASE_ERROR, CLASE_ETIQUETA, CLASE_TARJETA } from '../../comun/estilos';
import { formatearFecha } from '../../comun/fechas';
import { useDatos } from '../../comun/useDatos';
import { useSesion } from '../sesion/SesionContext';
import { relojesApi } from './relojesApi';
import { NOMBRE_ESTADO, type EstadoReloj, type RelojConPaciente } from './tipos';

/** Color de la etiqueta de cada estado. */
const COLOR_ESTADO: Record<EstadoReloj, string> = {
  OPERATIVO: 'bg-emerald-100 text-emerald-800',
  EN_REPARACION: 'bg-amber-100 text-amber-800',
  RETIRADO: 'bg-slate-200 text-slate-600',
};

/** true si el texto buscado está en el identificador, el modelo, la SIM o el nombre del usuario. */
function coincide(fila: RelojConPaciente, busqueda: string): boolean {
  const d = fila.reloj.datos;
  const texto = [d.identificadorReloj, d.modelo ?? '', d.numeroSim ?? '', fila.nombrePaciente ?? ''].join(' ');
  return texto.toLowerCase().includes(busqueda.toLowerCase());
}

/**
 * Inventario de relojes con búsqueda y filtro por estado. Para entregar un reloj a un usuario
 * se va a su ficha. El estado técnico (batería, última sincronización) se agregará más adelante.
 */
export function ListaRelojes() {
  const { puede } = useSesion();
  const { datos: relojes, error, cargando } = useDatos(relojesApi.listar);
  const [busqueda, setBusqueda] = useState('');
  const [estado, setEstado] = useState<EstadoReloj | ''>('');

  const visibles = useMemo(
    () =>
      (relojes ?? []).filter(
        (fila) =>
          (!estado || fila.reloj.datos.estado === estado) && (!busqueda.trim() || coincide(fila, busqueda.trim())),
      ),
    [relojes, estado, busqueda],
  );

  return (
    <section className={`${CLASE_TARJETA} space-y-4`}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-medium text-tinta">Relojes</h1>
          <p className="text-tinta">Inventario de relojes del programa y la persona usuaria que tiene cada uno.</p>
        </div>
        {puede('USUARIOS:CREAR') && (
          <Link to="/relojes/nuevo" className={CLASE_BOTON_PRINCIPAL}>
            Registrar reloj
          </Link>
        )}
      </div>

      <div className="flex flex-wrap items-end gap-3">
        <label className="block min-w-52 flex-1">
          <span className={CLASE_ETIQUETA}>Buscar reloj</span>
          <input
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            placeholder="Identificador, modelo, SIM o usuario"
            className={CLASE_CAMPO}
          />
        </label>
        <label className="block w-52">
          <span className={CLASE_ETIQUETA}>Estado</span>
          <select value={estado} onChange={(e) => setEstado(e.target.value as EstadoReloj | '')} className={CLASE_CAMPO}>
            <option value="">Todos</option>
            {(Object.keys(NOMBRE_ESTADO) as EstadoReloj[]).map((valor) => (
              <option key={valor} value={valor}>
                {NOMBRE_ESTADO[valor]}
              </option>
            ))}
          </select>
        </label>
      </div>

      {error && <p className={CLASE_ERROR}>{error}</p>}

      <div className="overflow-x-auto rounded-lg border border-slate-200">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Identificador</th>
              <th className="px-4 py-3 font-medium">Modelo</th>
              <th className="px-4 py-3 font-medium">SIM</th>
              <th className="px-4 py-3 font-medium">Plan vence</th>
              <th className="px-4 py-3 font-medium">Estado</th>
              <th className="px-4 py-3 font-medium">Usuario</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {visibles.map((fila) => {
              const d = fila.reloj.datos;
              return (
                <tr key={fila.reloj.id} className="border-b border-slate-100 last:border-0">
                  <td className="px-4 py-3 font-mono">{d.identificadorReloj}</td>
                  <td className="px-4 py-3">{d.modelo ?? '—'}</td>
                  <td className="px-4 py-3">{d.numeroSim ?? '—'}</td>
                  <td className="px-4 py-3">{d.fechaVencimientoPlan ? formatearFecha(d.fechaVencimientoPlan) : '—'}</td>
                  <td className="px-4 py-3">
                    <span className={`rounded-full px-2 py-0.5 text-xs ${COLOR_ESTADO[d.estado]}`}>
                      {NOMBRE_ESTADO[d.estado]}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    {fila.idPaciente ? (
                      <Link to={`/usuarios/${fila.idPaciente}`} className="text-sky-700 hover:underline">
                        {fila.nombrePaciente}
                      </Link>
                    ) : (
                      <span className="text-slate-400">Libre</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-right">
                    {puede('USUARIOS:EDITAR') && (
                      <Link to={`/relojes/${fila.reloj.id}/editar`} className="text-sky-700 hover:underline">
                        Editar
                      </Link>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        {cargando && <p className="px-4 py-3 text-slate-500">Cargando…</p>}
        {!cargando && relojes && visibles.length === 0 && (
          <p className="px-4 py-3 text-slate-500">
            {relojes.length === 0 ? 'Todavía no hay relojes registrados.' : 'Ningún reloj coincide con los filtros.'}
          </p>
        )}
      </div>
    </section>
  );
}
