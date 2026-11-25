import { Route, Routes } from 'react-router';
import { RutaProtegida } from '../../navegacion/RutaProtegida';
import { ListaRelojes } from './ListaRelojes';

/**
 * Sección "Relojes" (inventario). La lista abre el registro y la edición en un diálogo; /relojes/nuevo y
 * /relojes/:id/editar abren la lista con ese diálogo, para poder enlazarlos. Usan los permisos de USUARIOS.
 */
export function PaginaRelojes() {
  return (
    <Routes>
      <Route index element={<ListaRelojes />} />
      <Route
        path="nuevo"
        element={
          <RutaProtegida permiso="USUARIOS:CREAR">
            <ListaRelojes dialogo="nuevo" />
          </RutaProtegida>
        }
      />
      <Route
        path=":id/editar"
        element={
          <RutaProtegida permiso="USUARIOS:EDITAR">
            <ListaRelojes dialogo="editar" />
          </RutaProtegida>
        }
      />
    </Routes>
  );
}
