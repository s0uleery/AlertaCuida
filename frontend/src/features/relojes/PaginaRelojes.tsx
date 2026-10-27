import { Route, Routes } from 'react-router';
import { RutaProtegida } from '../../navegacion/RutaProtegida';
import { EdicionReloj } from './EdicionReloj';
import { ListaRelojes } from './ListaRelojes';

/**
 * Sección "Relojes" (inventario). Tiene sus propias rutas dentro de /relojes: la lista, el registro
 * y la edición. Usan los permisos del módulo USUARIOS.
 */
export function PaginaRelojes() {
  return (
    <Routes>
      <Route index element={<ListaRelojes />} />
      <Route
        path="nuevo"
        element={
          <RutaProtegida permiso="USUARIOS:CREAR">
            <EdicionReloj />
          </RutaProtegida>
        }
      />
      <Route
        path=":id/editar"
        element={
          <RutaProtegida permiso="USUARIOS:EDITAR">
            <EdicionReloj />
          </RutaProtegida>
        }
      />
    </Routes>
  );
}
