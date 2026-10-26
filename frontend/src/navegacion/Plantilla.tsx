import { NavLink, Outlet } from 'react-router';
import { useSesion } from '../features/sesion/SesionContext';
import { SECCIONES } from './menu';

/** Cómo se muestra cada rol en la barra superior. */
const NOMBRE_ROL: Record<string, string> = {
  ADMINISTRADOR: 'Administrador',
  COORDINADOR: 'Coordinador',
  PERSONAL: 'Personal',
};

/** Barra superior con el menú según los permisos del rol; cada página se dibuja en <Outlet />. */
export function Plantilla() {
  const { usuario, puede, salir } = useSesion();
  const visibles = SECCIONES.filter((seccion) => puede(seccion.permiso));

  return (
    <div className="min-h-screen bg-slate-100">
      <header className="bg-white shadow-sm">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3">
          <NavLink to="/" className="text-lg font-semibold text-sky-800">
            AlertaCuida
          </NavLink>
          <nav className="flex flex-1 flex-wrap gap-1">
            {visibles.map((seccion) => (
              <NavLink
                key={seccion.ruta}
                to={seccion.ruta}
                className={({ isActive }) =>
                  `rounded-md px-3 py-1.5 text-sm ${isActive ? 'bg-sky-100 text-sky-900' : 'text-slate-600 hover:bg-slate-100'}`
                }
              >
                {seccion.etiqueta}
              </NavLink>
            ))}
          </nav>
          <div className="flex items-center gap-3 text-sm">
            <span className="text-slate-700">
              {usuario?.nombre}{' '}
              <span className="text-slate-400">· {NOMBRE_ROL[usuario?.rol ?? ''] ?? usuario?.rol}</span>
            </span>
            <button onClick={salir} className="rounded-md border border-slate-300 px-3 py-1 hover:bg-slate-50">
              Salir
            </button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  );
}
