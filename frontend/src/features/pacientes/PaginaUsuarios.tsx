import { Route, Routes } from 'react-router';
import { RutaProtegida } from '../../navegacion/RutaProtegida';
import { FichaUsuario } from './FichaUsuario';
import { FormularioUsuario } from './FormularioUsuario';
import { ListaUsuarios } from './ListaUsuarios';

/**
 * Sección "Usuarios" (pacientes del programa). Tiene sus propias rutas dentro de /usuarios:
 * la lista, el alta, la ficha y la edición. Cada una exige su permiso del módulo USUARIOS.
 */
export function PaginaUsuarios() {
  return (
    <Routes>
      <Route index element={<ListaUsuarios />} />
      <Route
        path="nuevo"
        element={
          <RutaProtegida permiso="USUARIOS:CREAR">
            <FormularioUsuario />
          </RutaProtegida>
        }
      />
      <Route path=":id" element={<FichaUsuario />} />
      <Route
        path=":id/editar"
        element={
          <RutaProtegida permiso="USUARIOS:EDITAR">
            <FormularioUsuario />
          </RutaProtegida>
        }
      />
    </Routes>
  );
}
