import { Link, useNavigate, useParams } from 'react-router';
import { CLASE_ERROR, CLASE_TARJETA } from '../../comun/estilos';
import { useDatos } from '../../comun/useDatos';
import { FormularioReloj } from './FormularioReloj';
import { relojesApi } from './relojesApi';
import type { DatosReloj } from './tipos';

/**
 * Registro (/relojes/nuevo) y edición (/relojes/:id/editar) de un reloj. Al guardar vuelve a la lista.
 * Para editar se busca el reloj en la lista: el backend no tiene GET /api/relojes/{id}.
 */
export function EdicionReloj() {
  const { id } = useParams();
  const navegar = useNavigate();
  const { datos: relojes, error, cargando } = useDatos(
    () => (id ? relojesApi.listar() : Promise.resolve([])),
    id ?? 'nuevo',
  );

  const reloj = id ? relojes?.find((fila) => fila.reloj.id === id)?.reloj : undefined;
  const titulo = id ? `Editar ${reloj?.datos.identificadorReloj ?? 'reloj'}` : 'Nuevo reloj';

  /** Registra o edita y vuelve a la lista, que se carga de nuevo. */
  async function guardar(datos: DatosReloj) {
    if (id) await relojesApi.editar(id, datos);
    else await relojesApi.registrar(datos);
    navegar('/relojes');
  }

  return (
    <div className="space-y-4">
      <p className="text-slate-500">
        <Link to="/relojes" className="font-medium text-prlac hover:underline">
          Relojes
        </Link>{' '}
        › {titulo}
      </p>

      <section className={`${CLASE_TARJETA} space-y-4`}>
        <h1 className="text-2xl font-medium text-tinta">{titulo}</h1>
        {error && <p className={CLASE_ERROR}>{error}</p>}
        {id && cargando && <p className="text-slate-500">Cargando…</p>}
        {id && !cargando && !error && !reloj && <p className={CLASE_ERROR}>No se encontró el reloj.</p>}
        {(!id || reloj) && (
          <FormularioReloj
            key={id ?? 'nuevo'}
            inicial={reloj?.datos}
            alGuardar={guardar}
            alCancelar={() => navegar('/relojes')}
          />
        )}
      </section>
    </div>
  );
}
