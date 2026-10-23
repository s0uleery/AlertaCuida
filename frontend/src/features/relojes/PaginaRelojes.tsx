import { useState } from 'react';
import { Link } from 'react-router';
import { CLASE_BOTON_PRINCIPAL, CLASE_ERROR, CLASE_TARJETA } from '../../comun/estilos';
import { formatearFecha } from '../../comun/fechas';
import { useDatos } from '../../comun/useDatos';
import { useSesion } from '../sesion/SesionContext';
import { FormularioReloj } from './FormularioReloj';
import { relojesApi } from './relojesApi';
import { NOMBRE_ESTADO, type EstadoReloj, type RelojConPaciente } from './tipos';

/** Color de la etiqueta de cada estado. */
const COLOR_ESTADO: Record<EstadoReloj, string> = {
  OPERATIVO: 'bg-emerald-100 text-emerald-800',
  EN_REPARACION: 'bg-amber-100 text-amber-800',
  RETIRADO: 'bg-slate-200 text-slate-600',
};

/**
 * Inventario de relojes: lista, registro y edición. Para entregar un reloj a un usuario
 * se va a su ficha. El estado técnico (batería, última sincronización) se agregará más adelante.
 */
export function PaginaRelojes() {
  const { puede } = useSesion();
  const { datos: relojes, error, cargando, recargar } = useDatos(relojesApi.listar);
  /** null = sin formulario; 'nuevo' = registro; un reloj = edición de ese reloj */
  const [editando, setEditando] = useState<RelojConPaciente | 'nuevo' | null>(null);

  /** Registra o edita según lo que esté abierto, cierra el formulario y recarga la lista. */
  async function guardar(datos: Parameters<typeof relojesApi.registrar>[0]) {
    if (editando === 'nuevo') {
      await relojesApi.registrar(datos);
    } else if (editando) {
      await relojesApi.editar(editando.reloj.id, datos);
    }
    setEditando(null);
    recargar();
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-xl font-semibold text-slate-900">Relojes</h1>
        {puede('USUARIOS:CREAR') && !editando && (
          <button onClick={() => setEditando('nuevo')} className={CLASE_BOTON_PRINCIPAL}>
            Registrar reloj
          </button>
        )}
      </div>

      {editando && (
        <section className={`${CLASE_TARJETA} space-y-4`}>
          <h2 className="text-lg font-semibold text-slate-900">
            {editando === 'nuevo' ? 'Nuevo reloj' : `Editar ${editando.reloj.datos.identificadorReloj}`}
          </h2>
          <FormularioReloj
            key={editando === 'nuevo' ? 'nuevo' : editando.reloj.id}
            inicial={editando === 'nuevo' ? undefined : editando.reloj.datos}
            alGuardar={guardar}
            alCancelar={() => setEditando(null)}
          />
        </section>
      )}

      {error && <p className={CLASE_ERROR}>{error}</p>}

      <section className={`${CLASE_TARJETA} overflow-x-auto p-0`}>
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
            {relojes?.map((fila) => {
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
                      <button onClick={() => setEditando(fila)} className="text-sky-700 hover:underline">
                        Editar
                      </button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        {cargando && <p className="px-4 py-3 text-slate-500">Cargando…</p>}
        {relojes?.length === 0 && <p className="px-4 py-3 text-slate-500">Todavía no hay relojes registrados.</p>}
      </section>
    </div>
  );
}
