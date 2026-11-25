import { useState } from 'react';
import { ErrorApi } from '../../api/cliente';
import { useConfirmar } from '../../comun/Confirmacion';
import { CLASE_BOTON_SECUNDARIO, CLASE_ERROR } from '../../comun/estilos';
import { relojesApi } from './relojesApi';
import type { DatosReloj } from './tipos';

/**
 * Borrar un reloj desde su edición, con confirmación. Solo se borra uno que nunca tuvo usuario; si tuvo, el
 * backend responde CON_HISTORIAL y aquí se ofrece pasarlo a Retirado, que lo saca de servicio sin perder sus
 * lecturas ni alertas. Si está vinculado (EN_USO), hay que desvincularlo antes desde la ficha del usuario.
 *
 * @param alTerminar se llama después de borrarlo o retirarlo (vuelve a la lista)
 */
export function EliminarReloj({
  id,
  datos,
  alTerminar,
}: {
  id: string;
  datos: DatosReloj;
  alTerminar: () => void;
}) {
  const confirmar = useConfirmar();
  const [error, setError] = useState<string | null>(null);
  const [conHistorial, setConHistorial] = useState(false);
  const [trabajando, setTrabajando] = useState(false);

  /** Pide confirmación y borra; si no se puede, muestra por qué. */
  async function eliminar() {
    const confirmado = await confirmar({
      titulo: `¿Eliminar el reloj ${datos.identificadorReloj}?`,
      mensaje: 'Se borra del inventario. Solo se puede si nunca lo usó nadie.',
      textoConfirmar: 'Eliminar',
      peligro: true,
    });
    if (!confirmado) return;
    await ejecutar(async () => {
      try {
        await relojesApi.eliminar(id);
        alTerminar();
      } catch (e) {
        if (e instanceof ErrorApi && e.codigo === 'CON_HISTORIAL') setConHistorial(true);
        throw e;
      }
    });
  }

  /** Lo pasa a Retirado con el resto de sus datos tal cual. */
  async function retirar() {
    await ejecutar(async () => {
      await relojesApi.editar(id, { ...datos, estado: 'RETIRADO' });
      alTerminar();
    });
  }

  /** Corre la acción mostrando el error del backend, si lo hay. */
  async function ejecutar(accion: () => Promise<void>) {
    setTrabajando(true);
    setError(null);
    try {
      await accion();
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.');
    } finally {
      setTrabajando(false);
    }
  }

  return (
    <div className="space-y-3 border-t border-slate-200 pt-4">
      <h2 className="text-lg font-medium text-tinta">Eliminar reloj</h2>
      {error && (
        <p role="alert" className={CLASE_ERROR}>
          {error}
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        {conHistorial && datos.estado !== 'RETIRADO' ? (
          <button type="button" onClick={retirar} disabled={trabajando} className={CLASE_BOTON_SECUNDARIO}>
            Pasar a Retirado
          </button>
        ) : (
          !conHistorial && (
            <button
              type="button"
              onClick={eliminar}
              disabled={trabajando}
              className={`${CLASE_BOTON_SECUNDARIO} border-red-300 text-red-700 hover:bg-red-50`}
            >
              Eliminar reloj
            </button>
          )
        )}
      </div>
    </div>
  );
}
