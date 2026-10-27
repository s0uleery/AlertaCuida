import { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { CLASE_BOTON_PRINCIPAL, CLASE_CAMPO, CLASE_ERROR, CLASE_TARJETA } from '../../comun/estilos';
import { useDatos } from '../../comun/useDatos';
import { useSesion } from '../sesion/SesionContext';
import { coincide } from './busqueda';
import { pacientesApi } from './pacientesApi';

/** Cómo se muestra cada nivel de dependencia. */
const NIVEL: Record<string, string> = { MODERADA: 'Moderada', SEVERA: 'Severa' };

/**
 * Lista de usuarios del programa con búsqueda. Se filtra aquí y no en el backend: los
 * nombres irán cifrados en la base y el piloto tiene pocos usuarios.
 */
export function ListaUsuarios() {
  const { puede } = useSesion();
  const [incluirBajas, setIncluirBajas] = useState(false);
  const [busqueda, setBusqueda] = useState('');
  const { datos, error, cargando } = useDatos(() => pacientesApi.listar(incluirBajas), String(incluirBajas));

  const visibles = useMemo(
    () => (datos ?? []).filter((fila) => !busqueda.trim() || coincide(fila, busqueda.trim())),
    [datos, busqueda],
  );

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-xl font-semibold text-slate-900">Usuarios</h1>
        {puede('USUARIOS:CREAR') && (
          <Link to="/usuarios/nuevo" className={CLASE_BOTON_PRINCIPAL}>
            Nuevo usuario
          </Link>
        )}
      </div>

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

      <section className={`${CLASE_TARJETA} overflow-x-auto p-0`}>
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Nombre</th>
              <th className="px-4 py-3 font-medium">RUN</th>
              <th className="px-4 py-3 font-medium">Edad</th>
              <th className="px-4 py-3 font-medium">Dependencia</th>
              <th className="px-4 py-3 font-medium">Reloj</th>
            </tr>
          </thead>
          <tbody>
            {visibles.map(({ paciente, edad, relojActual }) => (
              <tr key={paciente.id} className="border-b border-slate-100 last:border-0">
                <td className="px-4 py-3">
                  <Link to={`/usuarios/${paciente.id}`} className="font-medium text-sky-700 hover:underline">
                    {paciente.datos.apellidos}, {paciente.datos.nombres}
                  </Link>
                  {paciente.fechaBaja && (
                    <span className="ml-2 rounded-full bg-slate-200 px-2 py-0.5 text-xs text-slate-600">De baja</span>
                  )}
                  {!paciente.datos.fechaConsentimiento && !paciente.fechaBaja && (
                    <span className="ml-2 rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-800">
                      Sin consentimiento
                    </span>
                  )}
                </td>
                <td className="px-4 py-3 font-mono">{paciente.datos.run}</td>
                <td className="px-4 py-3">{edad ?? '—'}</td>
                <td className="px-4 py-3">
                  {paciente.datos.nivelDependencia ? NIVEL[paciente.datos.nivelDependencia] : 'Por evaluar'}
                </td>
                <td className="px-4 py-3 font-mono">{relojActual ?? <span className="text-slate-400">Sin reloj</span>}</td>
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
      </section>
    </div>
  );
}
