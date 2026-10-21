import { useSesion } from '../sesion/SesionContext';

/** Página de prueba: muestra quién inició sesión y qué permisos trae su rol. */
export function PaginaInicio() {
  const { usuario } = useSesion();
  if (!usuario) return null;

  return (
    <section className="space-y-4 rounded-xl bg-white p-6 shadow-sm">
      <h1 className="text-xl font-semibold text-slate-900">Hola, {usuario.nombre}</h1>
      <p className="text-slate-600">
        Rol: <strong>{usuario.rol}</strong>. El menú de arriba muestra solo las secciones que tu rol puede ver.
      </p>
      <div>
        <h2 className="mb-2 text-sm font-medium text-slate-700">Permisos ({usuario.permisos.length})</h2>
        <ul className="flex flex-wrap gap-2">
          {usuario.permisos.map((permiso) => (
            <li key={permiso} className="rounded-full bg-slate-100 px-3 py-1 font-mono text-xs text-slate-700">
              {permiso}
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
