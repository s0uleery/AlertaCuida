import { Route, Routes } from 'react-router';
import { PaginaPendiente } from '../inicio/PaginaPendiente';
import { ListaAlertas } from './ListaAlertas';

/**
 * Sección "Alertas". Tiene sus propias rutas dentro de /alertas: la lista y la ficha de cada alerta. La
 * ficha (línea de tiempo y acciones) todavía no está construida; la portada ya enlaza a ella.
 */
export function PaginaAlertas() {
  return (
    <Routes>
      <Route index element={<ListaAlertas />} />
      <Route path=":id" element={<PaginaPendiente titulo="Ficha de la alerta" />} />
    </Routes>
  );
}
