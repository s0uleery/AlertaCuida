/** Lugar reservado para las secciones que aún no se construyen. */
export function PaginaPendiente({ titulo }: { titulo: string }) {
  return (
    <section className="rounded-xl bg-white p-6 shadow-sm">
      <h1 className="text-xl font-semibold text-slate-900">{titulo}</h1>
      <p className="mt-2 text-slate-500">Esta sección todavía no está construida.</p>
    </section>
  );
}
