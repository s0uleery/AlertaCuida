import type { ReactNode } from 'react';
import { Link } from 'react-router';

/** Bloque blanco con borde: la unidad de contenido de cada pantalla. */
export function Panel({
  titulo,
  acciones,
  children,
  className = '',
}: {
  titulo?: ReactNode;
  /** Botones a la derecha del título */
  acciones?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`rounded-panel border border-linea bg-superficie p-5 ${className}`}>
      {(titulo || acciones) && (
        <div className="mb-3.5 flex flex-wrap items-center justify-between gap-3">
          {titulo && <h2 className="text-[14.5px] font-bold text-navy">{titulo}</h2>}
          {acciones && <div className="flex flex-wrap gap-2">{acciones}</div>}
        </div>
      )}
      {children}
    </section>
  );
}

/** Cabecera de una pantalla: título, explicación y acciones, con una línea debajo. */
export function EncabezadoVista({
  titulo,
  subtitulo,
  acciones,
  migas,
}: {
  titulo: ReactNode;
  subtitulo?: ReactNode;
  acciones?: ReactNode;
  /** Camino de vuelta, ej. [{ texto: 'Alertas', a: '/alertas' }] */
  migas?: { texto: string; a: string }[];
}) {
  return (
    <div className="mb-5 border-b border-linea pb-4">
      {migas && migas.length > 0 && <Migas migas={migas} />}
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="min-w-0">
          <h1 className="text-xl font-bold tracking-tight text-navy">{titulo}</h1>
          {subtitulo && <p className="mt-0.5 text-[13px] text-apagado">{subtitulo}</p>}
        </div>
        {acciones && <div className="flex flex-wrap gap-2">{acciones}</div>}
      </div>
    </div>
  );
}

/** Migas de pan: el camino hacia la pantalla actual. */
export function Migas({ migas }: { migas: { texto: string; a: string }[] }) {
  return (
    <nav aria-label="Ruta" className="mb-2 text-[12.5px] text-apagado">
      {migas.map((miga, i) => (
        <span key={miga.a}>
          {i > 0 && <span aria-hidden="true"> / </span>}
          <Link to={miga.a} className="font-bold text-teal-oscuro hover:underline">
            {miga.texto}
          </Link>
        </span>
      ))}
    </nav>
  );
}

/** Lista de datos "etiqueta: valor" en dos columnas, como la ficha del mockup. */
export function ListaDatos({ datos }: { datos: [string, ReactNode][] }) {
  return (
    <dl className="grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-2">
      {datos.map(([etiqueta, valor]) => (
        <div key={etiqueta}>
          <dt className="text-[11px] font-bold tracking-wide text-apagado uppercase">{etiqueta}</dt>
          <dd className="mt-0.5 font-medium text-navy">{valor ?? <span className="text-apagado">—</span>}</dd>
        </div>
      ))}
    </dl>
  );
}
