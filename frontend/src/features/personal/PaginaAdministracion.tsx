import { Route, Routes } from 'react-router';
import { RutaProtegida } from '../../navegacion/RutaProtegida';
import { NuevaCuenta } from './NuevaCuenta';
import { PanelAdministracion } from './PanelAdministracion';

/**
 * Sección Administración. Rutas dentro de /administracion: el panel con sus pestañas (perfiles,
 * permisos y auditoría) y el alta de una cuenta del equipo ("Agregar perfil").
 */
export function PaginaAdministracion() {
  return (
    <Routes>
      <Route index element={<PanelAdministracion />} />
      <Route
        path="nuevo"
        element={
          <RutaProtegida permiso="ADMINISTRACION:CREAR">
            <NuevaCuenta />
          </RutaProtegida>
        }
      />
    </Routes>
  );
}
