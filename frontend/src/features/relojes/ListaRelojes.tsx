import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { CLASE_CAMPO, CLASE_ETIQUETA } from '../../comun/estilos';
import { formatearFecha } from '../../comun/fechas';
import { Aviso, Cargando, Vacio } from '../../comun/ui/Aviso';
import { claseBoton } from '../../comun/ui/Boton';
import { Dialogo } from '../../comun/ui/Dialogo';
import { Insignia, type TonoInsignia } from '../../comun/ui/Insignia';
import { EncabezadoVista } from '../../comun/ui/Panel';
import { Paginacion, TABLA, Tabla, usePaginacion } from '../../comun/ui/Tabla';
import { useToast } from '../../comun/ui/Toast';
import { useDatos } from '../../comun/useDatos';
import { useSesion } from '../sesion/SesionContext';
import { EliminarReloj } from './EliminarReloj';
import { FormularioReloj } from './FormularioReloj';
import { relojesApi } from './relojesApi';
import { SemaforoReloj } from './SemaforoReloj';
import { NOMBRE_ESTADO, type DatosReloj, type EstadoReloj, type RelojConPaciente } from './tipos';

/** Tono de la insignia de cada estado. */
const TONO_ESTADO: Record<EstadoReloj, TonoInsignia> = {
  OPERATIVO: 'leve',
  EN_REPARACION: 'media',
  RETIRADO: 'neutra',
};

/** true si el texto buscado está en el identificador, el modelo, la SIM o el nombre del usuario. */
function coincide(fila: RelojConPaciente, busqueda: string): boolean {
  const d = fila.reloj.datos;
  const texto = [d.identificadorReloj, d.modelo ?? '', d.numeroSim ?? '', fila.nombrePaciente ?? ''].join(' ');
  return texto.toLowerCase().includes(busqueda.toLowerCase());
}

/**
 * Inventario de relojes del mockup, con búsqueda, filtro por estado y paginación. La columna "Funcionamiento"
 * muestra el semáforo de cada reloj (al pasar el mouse, por qué). Registrar y editar se hacen en un diálogo
 * (también desde /relojes/nuevo y /relojes/:id/editar); eliminar está dentro de la edición. Para entregar un
 * reloj a un usuario se va a su ficha, porque pide el código de emparejamiento que muestra el reloj.
 */
export function ListaRelojes({ dialogo }: { dialogo?: 'nuevo' | 'editar' }) {
  const { puede } = useSesion();
  const { id } = useParams();
  const navegar = useNavigate();
  const avisar = useToast();
  const { datos: relojes, error, cargando, recargar } = useDatos(relojesApi.listar);
  // El semáforo se carga aparte: si falla, la lista se muestra igual, sin el círculo
  const { datos: estados } = useDatos(relojesApi.estado);
  const estadoDe = useMemo(() => new Map((estados ?? []).map((estado) => [estado.idDispositivo, estado])), [estados]);
  const [busqueda, setBusqueda] = useState('');
  const [estado, setEstado] = useState<EstadoReloj | ''>('');

  const visibles = useMemo(
    () =>
      (relojes ?? []).filter(
        (fila) =>
          (!estado || fila.reloj.datos.estado === estado) && (!busqueda.trim() || coincide(fila, busqueda.trim())),
      ),
    [relojes, estado, busqueda],
  );
  const paginacion = usePaginacion(visibles, 10);
  const enUso = (relojes ?? []).filter((fila) => fila.idPaciente).length;
  const editado = dialogo === 'editar' ? relojes?.find((fila) => fila.reloj.id === id)?.reloj : undefined;

  /** Cierra el diálogo y vuelve a la lista. */
  function cerrar() {
    navegar('/relojes');
  }

  /** Registra o edita, avisa y vuelve a la lista recargada. */
  async function guardar(datos: DatosReloj) {
    if (dialogo === 'editar' && id) {
      await relojesApi.editar(id, datos);
      avisar(`Reloj ${datos.identificadorReloj} actualizado`);
    } else {
      await relojesApi.registrar(datos);
      avisar(`Reloj ${datos.identificadorReloj} registrado`);
    }
    recargar();
    cerrar();
  }

  return (
    <>
      <EncabezadoVista
        titulo="Relojes"
        subtitulo={
          <>
            Inventario de relojes del programa y la persona usuaria vinculada a cada dispositivo.
            {relojes && (
              <>
                {' '}
                <b className="cifra text-navy">{enUso}</b> de <b className="cifra text-navy">{relojes.length}</b> en uso.
              </>
            )}
          </>
        }
        acciones={
          puede('USUARIOS:CREAR') && (
            <Link to="/relojes/nuevo" className={claseBoton('principal')}>
              + Registrar reloj
            </Link>
          )
        }
      />

      <div className="mb-4 flex flex-wrap items-end gap-3">
        <label className="block min-w-56 flex-1">
          <span className={CLASE_ETIQUETA}>Buscar reloj</span>
          <input
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            placeholder="Identificador, modelo, SIM o usuario"
            className={CLASE_CAMPO}
          />
        </label>
        <label className="block w-52">
          <span className={CLASE_ETIQUETA}>Estado</span>
          <select value={estado} onChange={(e) => setEstado(e.target.value as EstadoReloj | '')} className={CLASE_CAMPO}>
            <option value="">Todos</option>
            {(Object.keys(NOMBRE_ESTADO) as EstadoReloj[]).map((valor) => (
              <option key={valor} value={valor}>
                {NOMBRE_ESTADO[valor]}
              </option>
            ))}
          </select>
        </label>
      </div>

      {error && <Aviso tono="error" className="mb-4">{error}</Aviso>}

      {cargando && !relojes ? (
        <Cargando />
      ) : visibles.length === 0 ? (
        <Vacio>
          {relojes?.length === 0 ? 'Todavía no hay relojes registrados en el inventario.' : 'Ningún reloj coincide con los filtros.'}
        </Vacio>
      ) : (
        <>
          <Tabla
            columnas={['Identificador', 'Modelo', 'SIM', 'Plan vence', 'Estado', 'Funcionamiento', 'Usuario', null]}
            etiqueta="Inventario de relojes"
          >
            {paginacion.visibles.map((fila) => {
              const d = fila.reloj.datos;
              return (
                <tr key={fila.reloj.id} className={TABLA.fila}>
                  <td className={`${TABLA.td} cifra font-semibold text-navy`}>{d.identificadorReloj}</td>
                  <td className={TABLA.td}>{d.modelo ?? '—'}</td>
                  <td className={`${TABLA.td} cifra`}>{d.numeroSim ?? '—'}</td>
                  <td className={`${TABLA.td} cifra`}>{d.fechaVencimientoPlan ? formatearFecha(d.fechaVencimientoPlan) : '—'}</td>
                  <td className={TABLA.td}>
                    <Insignia tono={TONO_ESTADO[d.estado]}>{NOMBRE_ESTADO[d.estado]}</Insignia>
                  </td>
                  <td className={TABLA.td}>
                    <SemaforoReloj estado={estadoDe.get(fila.reloj.id)} />
                  </td>
                  <td className={TABLA.td}>
                    {fila.idPaciente ? (
                      <Link to={`/usuarios/${fila.idPaciente}`} className="font-bold text-navy hover:underline">
                        {fila.nombrePaciente}
                      </Link>
                    ) : (
                      <span className="text-apagado">Sin asignar</span>
                    )}
                  </td>
                  <td className={`${TABLA.td} text-right`}>
                    {puede('USUARIOS:EDITAR') && (
                      <Link to={`/relojes/${fila.reloj.id}/editar`} className={claseBoton('contorno', true)}>
                        Editar
                      </Link>
                    )}
                  </td>
                </tr>
              );
            })}
          </Tabla>
          <Paginacion estado={paginacion} />
        </>
      )}

      {dialogo === 'nuevo' && (
        <Dialogo titulo="Registrar reloj" alCerrar={cerrar} ancho="max-w-3xl">
          <FormularioReloj alGuardar={guardar} alCancelar={cerrar} />
        </Dialogo>
      )}
      {dialogo === 'editar' && relojes && (
        <Dialogo titulo={editado ? `Editar reloj · ${editado.datos.identificadorReloj}` : 'Editar reloj'} alCerrar={cerrar} ancho="max-w-3xl">
          {editado && id ? (
            <div className="space-y-5">
              <FormularioReloj key={id} inicial={editado.datos} alGuardar={guardar} alCancelar={cerrar} />
              {puede('USUARIOS:ELIMINAR') && (
                <EliminarReloj
                  id={id}
                  datos={editado.datos}
                  alTerminar={() => {
                    recargar();
                    cerrar();
                  }}
                />
              )}
            </div>
          ) : (
            <Aviso tono="error">No se encontró el reloj.</Aviso>
          )}
        </Dialogo>
      )}
    </>
  );
}
