/** Una de las dos cantidades que compara la tarjeta. */
interface Parte {
  etiqueta: string;
  cantidad: number;
}

/**
 * Recuadro con un anillo y dos barras, como en el mockup: la primera parte en color fuerte y la
 * segunda en color suave.
 *
 * @param colores fuerte y suave, para el anillo y las barras
 */
export function TarjetaResumen({
  titulo,
  primera,
  segunda,
  colores,
}: {
  titulo: string;
  primera: Parte;
  segunda: Parte;
  colores: { fuerte: string; suave: string };
}) {
  const total = primera.cantidad + segunda.cantidad;
  const porcentaje = total === 0 ? 0 : (primera.cantidad / total) * 100;
  // Sin datos, el anillo queda gris
  const fondo =
    total === 0
      ? '#e2e8f0'
      : `conic-gradient(${colores.fuerte} 0 ${porcentaje}%, ${colores.suave} 0)`;
  // El hueco del centro se recorta con una máscara
  const hueco = 'radial-gradient(circle 30px, transparent 98%, #000 100%)';

  return (
    <div className="rounded border border-slate-300 px-5 py-4">
      <h2 className="text-base font-bold text-tinta">{titulo}</h2>
      <div className="mt-3 flex items-center gap-5">
        <div
          className="size-24 flex-none rounded-full"
          style={{ background: fondo, mask: hueco, WebkitMask: hueco }}
          role="img"
          aria-label={`${primera.cantidad} ${primera.etiqueta.toLowerCase()}, ${segunda.cantidad} ${segunda.etiqueta.toLowerCase()}`}
        />
        <div className="grid flex-1 gap-2">
          <div className="flex gap-3 rounded-sm px-2.5 py-1 text-white" style={{ background: colores.fuerte }}>
            <b>{primera.cantidad}</b> {primera.etiqueta}
          </div>
          <div
            className="flex gap-3 rounded-sm border-l-6 bg-white px-2.5 py-1 text-slate-800 shadow-md"
            style={{ borderColor: colores.suave }}
          >
            <b>{segunda.cantidad}</b> {segunda.etiqueta}
          </div>
        </div>
      </div>
    </div>
  );
}
