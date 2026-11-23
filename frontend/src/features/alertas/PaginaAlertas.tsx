import { Route, Routes } from 'react-router';
import { FichaAlerta } from './FichaAlerta';
import { ListaAlertas } from './ListaAlertas';

/**
 * Sección "Alertas". Tiene sus propias rutas dentro de /alertas: la lista y la ficha de cada alerta, con su
 * evidencia, su línea de tiempo y las acciones del equipo.
 */
export function PaginaAlertas() {
  return (
    <Routes>
      <Route index element={<ListaAlertas />} />
      <Route path=":id" element={<FichaAlerta />} />
    </Routes>
  );
}
