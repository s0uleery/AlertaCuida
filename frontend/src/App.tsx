import { BrowserRouter, Navigate, Route, Routes } from 'react-router';
import { PaginaInicio } from './features/inicio/PaginaInicio';
import { PaginaPendiente } from './features/inicio/PaginaPendiente';
import { PaginaLogin } from './features/sesion/PaginaLogin';
import { SesionProvider } from './features/sesion/SesionContext';
import { SECCIONES } from './navegacion/menu';
import { Plantilla } from './navegacion/Plantilla';
import { RutaProtegida } from './navegacion/RutaProtegida';

export function App() {
  return (
    <SesionProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<PaginaLogin />} />
          <Route
            element={
              <RutaProtegida>
                <Plantilla />
              </RutaProtegida>
            }
          >
            <Route index element={<PaginaInicio />} />
            {SECCIONES.map((seccion) => (
              <Route
                key={seccion.ruta}
                path={seccion.ruta}
                element={
                  <RutaProtegida permiso={seccion.permiso}>
                    <PaginaPendiente titulo={seccion.etiqueta} />
                  </RutaProtegida>
                }
              />
            ))}
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </SesionProvider>
  );
}
