import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { useSesion } from '../features/sesion/SesionContext';
import type { Permiso } from '../features/sesion/tipos';

/** Sin sesión, al login (y de vuelta aquí después). Sin el permiso, al inicio. */
export function RutaProtegida({ permiso, children }: { permiso?: Permiso; children: ReactNode }) {
  const { usuario, cargando, puede } = useSesion();
  const ubicacion = useLocation();

  if (cargando) {
    return <p className="p-8 text-slate-500">Cargando…</p>;
  }
  if (!usuario) {
    return <Navigate to="/login" replace state={{ desde: ubicacion.pathname }} />;
  }
  if (permiso && !puede(permiso)) {
    return <Navigate to="/" replace />;
  }
  return children;
}
