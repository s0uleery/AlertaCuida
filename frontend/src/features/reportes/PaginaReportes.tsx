import { Route, Routes } from 'react-router';
import { ListaReportes } from './ListaReportes';
import { ReporteUsuario } from './ReporteUsuario';

/**
 * Sección "Reportes". Rutas dentro de /reportes: la lista con sus pestañas y el reporte de
 * un usuario (/reportes/:id/diario o /reportes/:id/semanal).
 */
export function PaginaReportes() {
  return (
    <Routes>
      <Route index element={<ListaReportes />} />
      <Route path=":id/:periodo" element={<ReporteUsuario />} />
    </Routes>
  );
}
