import { useEffect, useState, type ReactNode } from 'react';

/** Clases de una tabla del sistema de diseño: encabezado gris en mayúsculas, filas con línea y hover turquesa. */
export const TABLA = {
  contenedor: 'overflow-x-auto rounded-panel border border-linea bg-superficie',
  tabla: 'w-full min-w-[640px] border-collapse text-left text-[13px]',
  th: 'border-b border-linea bg-superficie-sub px-3 py-2.5 text-[11px] font-bold tracking-wide whitespace-nowrap text-navy uppercase',
  td: 'border-b border-linea px-3 py-2.5 align-middle',
  fila: 'last:[&>td]:border-b-0',
  filaClic: 'cursor-pointer last:[&>td]:border-b-0 hover:bg-teal-tinte focus-visible:bg-teal-tinte',
} as const;

/** Tabla con su contenedor desplazable (en celular se desplaza de lado en vez de romper la página). */
export function Tabla({
  columnas,
  children,
  etiqueta,
}: {
  /** Títulos de las columnas; null deja el título vacío (columna de acciones) */
  columnas: (string | null)[];
  children: ReactNode;
  /** Descripción para lectores de pantalla */
  etiqueta?: string;
}) {
  return (
    <div className={TABLA.contenedor}>
      <table className={TABLA.tabla} aria-label={etiqueta}>
        <thead>
          <tr>
            {columnas.map((columna, i) => (
              <th key={columna ?? `vacia-${i}`} scope="col" className={TABLA.th}>
                {columna ?? <span className="sr-only">Acciones</span>}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}

/** Estado de la paginación de una lista. */
export interface EstadoPaginacion<T> {
  /** Los elementos de la página actual */
  visibles: T[];
  pagina: number;
  paginas: number;
  limite: number;
  total: number;
  setPagina: (pagina: number) => void;
  setLimite: (limite: number) => void;
}

/**
 * Pagina una lista ya filtrada. Vuelve a la primera página cuando cambia la lista (por ejemplo, al filtrar).
 *
 * @param limiteInicial cuántos por página; 0 = todos
 */
export function usePaginacion<T>(items: T[], limiteInicial = 10): EstadoPaginacion<T> {
  const [pagina, setPagina] = useState(1);
  const [limite, setLimite] = useState(limiteInicial);
  const total = items.length;
  const paginas = limite === 0 ? 1 : Math.max(1, Math.ceil(total / limite));

  useEffect(() => {
    setPagina(1);
  }, [items, limite]);

  const actual = Math.min(pagina, paginas);
  const visibles = limite === 0 ? items : items.slice((actual - 1) * limite, actual * limite);
  return { visibles, pagina: actual, paginas, limite, total, setPagina, setLimite };
}

/** Pie de una lista: "Mostrando X–Y de Z", cuántos por página y anterior/siguiente. */
export function Paginacion<T>({ estado, opciones = [5, 10, 20, 50] }: { estado: EstadoPaginacion<T>; opciones?: number[] }) {
  const { pagina, paginas, limite, total, setPagina, setLimite } = estado;
  if (total === 0) return null;
  const desde = limite === 0 ? 1 : (pagina - 1) * limite + 1;
  const hasta = limite === 0 ? total : Math.min(pagina * limite, total);
  const boton =
    'rounded-[5px] border border-linea-oscura bg-superficie px-2.5 py-1 text-[12.5px] text-navy hover:enabled:bg-superficie-sub disabled:opacity-40';
  return (
    <div className="mt-3 flex flex-wrap items-center justify-between gap-3 text-[12.5px] text-apagado">
      <span>
        Mostrando <span className="cifra">{desde}–{hasta}</span> de <span className="cifra">{total}</span>
      </span>
      <div className="flex flex-wrap items-center gap-2">
        <label className="flex items-center gap-1.5">
          Por página
          <select
            value={limite}
            onChange={(e) => setLimite(Number(e.target.value))}
            className="rounded-[5px] border border-linea-oscura bg-superficie px-1.5 py-1 text-navy"
          >
            {opciones.map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
            <option value={0}>Todos</option>
          </select>
        </label>
        {paginas > 1 && (
          <>
            <button type="button" className={boton} disabled={pagina <= 1} onClick={() => setPagina(pagina - 1)}>
              ← Anterior
            </button>
            <span className="cifra" aria-live="polite">
              {pagina}/{paginas}
            </span>
            <button type="button" className={boton} disabled={pagina >= paginas} onClick={() => setPagina(pagina + 1)}>
              Siguiente →
            </button>
          </>
        )}
      </div>
    </div>
  );
}
