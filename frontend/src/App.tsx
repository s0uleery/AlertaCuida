import { BrowserRouter, Navigate, Route, Routes } from 'react-router';
import { ConfirmacionProvider } from './comun/Confirmacion';
import { PaginaInicio } from './features/inicio/PaginaInicio';
import { PaginaPendiente } from './features/inicio/PaginaPendiente';
import { PaginaActivar } from './features/personal/PaginaActivar';
import { PaginaResponder } from './features/respuesta/PaginaResponder';
import { PaginaLogin } from './features/sesion/PaginaLogin';
import { SesionProvider } from './features/sesion/SesionContext';
import { SECCIONES } from './navegacion/menu';
import { Plantilla } from './navegacion/Plantilla';
import { RutaProtegida } from './navegacion/RutaProtegida';

/**
 * Rutas del dashboard. Públicas: /login, /activar y /responder (la del contacto que responde a su correo). Las demás exigen sesión y, cada sección,
 * su permiso (ver navegacion/menu.ts).
 */
export function App() {
  return (
    <SesionProvider>
      <ConfirmacionProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<PaginaLogin />} />
            <Route path="/activar" element={<PaginaActivar />} />
            <Route path="/responder" element={<PaginaResponder />} />
            <Route
              element={
                <RutaProtegida>
                  <Plantilla />
                </RutaProtegida>
              }
            >
              <Route index element={<PaginaInicio />} />
              {SECCIONES.map(({ ruta, etiqueta, permiso, pagina: Pagina }) => (
                <Route
                  key={ruta}
                  // "/*": la sección puede tener rutas internas (ej. /usuarios/:id)
                  path={`${ruta}/*`}
                  element={
                    <RutaProtegida permiso={permiso}>
                      {Pagina ? <Pagina /> : <PaginaPendiente titulo={etiqueta} />}
                    </RutaProtegida>
                  }
                />
              ))}
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </ConfirmacionProvider>
    </SesionProvider>
  );
}
