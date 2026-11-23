import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { ErrorApi } from '../../api/cliente';
import { useConfirmar } from '../../comun/Confirmacion';
import { CLASE_BOTON_PRINCIPAL, CLASE_BOTON_SECUNDARIO, CLASE_ERROR, CLASE_TARJETA } from '../../comun/estilos';
import { formatearAntiguedad, formatearFechaHora } from '../../comun/fechas';
import { useDatos } from '../../comun/useDatos';
import { useSesion } from '../sesion/SesionContext';
import { alertasApi } from './alertasApi';
import { DialogoResolver } from './DialogoResolver';
import { CLASIFICACION, CONDICION, describirContacto, ESTADO, InsigniaGravedad, InsigniaSimulacro } from './etiquetas';
import { EvidenciaDeAlerta } from './EvidenciaDeAlerta';
import { LineaTiempo } from './LineaTiempo';
import type { ClasificacionAlerta, DetalleAlerta } from './tipos';

/** @returns el mensaje del backend, o uno genérico si no hubo conexión */
function mensajeDe(e: unknown): string {
  return e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.';
}

/** Qué acciones admite la alerta en su estado actual (el backend igual las valida). */
function accionesPosibles(alerta: DetalleAlerta) {
  const cancelada = alerta.estadoContacto === 'CANCELADA';
  const abierta = alerta.estadoAtencion === 'SIN_ASIGNAR' || alerta.estadoAtencion === 'EN_ATENCION';
  return {
    asumir: !cancelada && alerta.estadoAtencion === 'SIN_ASIGNAR',
    resolver: !cancelada && abierta,
    cerrarSeguimiento: alerta.estadoAtencion === 'EN_SEGUIMIENTO',
  };
}

/**
 * Ficha de una alerta (/alertas/:id): quién es el usuario, en qué está la alerta, la evidencia que la explica
 * y su recorrido completo. Con ALERTAS:EDITAR se puede asumir el caso, resolverlo (el reloj deja de avisar) y
 * cerrar el seguimiento de una positiva.
 */
export function FichaAlerta() {
  const { id = '' } = useParams();
  const { puede } = useSesion();
  const confirmar = useConfirmar();
  const { datos: alerta, error, cargando, recargar } = useDatos(() => alertasApi.detalle(id), id);
  const [errorAccion, setErrorAccion] = useState<string | null>(null);
  const [resolviendo, setResolviendo] = useState(false);
  const [guardando, setGuardando] = useState(false);

  if (cargando && !alerta) return <p className="text-slate-500">Cargando…</p>;
  if (error || !alerta) return <p className={CLASE_ERROR}>{error ?? 'No se encontró la alerta.'}</p>;

  const acciones = puede('ALERTAS:EDITAR') ? accionesPosibles(alerta) : null;
  const esSalud = alerta.categoria === 'SALUD';
  const idAlerta = alerta.id;

  /** Ejecuta una acción, muestra su error si falla y recarga la ficha. */
  async function ejecutar(accion: () => Promise<void>): Promise<boolean> {
    setGuardando(true);
    setErrorAccion(null);
    try {
      await accion();
      recargar();
      return true;
    } catch (e) {
      setErrorAccion(mensajeDe(e));
      // Si otra persona la tomó o la cerró, la ficha debe mostrarlo
      recargar();
      return false;
    } finally {
      setGuardando(false);
    }
  }

  /** Resuelve con lo escrito en el diálogo; si falla, el diálogo sigue abierto con el error. */
  async function resolver(clasificacion: ClasificacionAlerta | null, observaciones: string) {
    if (await ejecutar(() => alertasApi.resolver(idAlerta, clasificacion, observaciones))) {
      setResolviendo(false);
    }
  }

  /** Pide confirmación y cierra el seguimiento. */
  async function cerrarSeguimiento() {
    const confirmado = await confirmar({
      titulo: '¿Cerrar el seguimiento?',
      mensaje: 'La alerta quedará cerrada.',
      textoConfirmar: 'Cerrar seguimiento',
    });
    if (confirmado) await ejecutar(() => alertasApi.cerrarSeguimiento(idAlerta));
  }

  const { paciente, atencion } = alerta;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <Link to="/alertas" className="text-sm text-sky-700 hover:underline">
            ← Alertas
          </Link>
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-xl font-semibold text-slate-900">Alerta #{alerta.numero}</h1>
            <InsigniaGravedad severidad={alerta.severidad} />
            {alerta.simulacro && <InsigniaSimulacro />}
          </div>
          <p className="text-tinta">{CONDICION[alerta.tipoEvento]}</p>
          <p className="text-sm text-slate-600">
            {puede('USUARIOS:VER') ? (
              <Link to={`/usuarios/${paciente.id}`} className="text-sky-700 hover:underline">
                {paciente.nombres} {paciente.apellidos}
              </Link>
            ) : (
              `${paciente.nombres} ${paciente.apellidos}`
            )}
            {paciente.edad !== null && `, ${paciente.edad} años`} · recibida el {formatearFechaHora(alerta.recepcion)} (
            {formatearAntiguedad(alerta.recepcion)})
          </p>
        </div>

        {acciones && (
          <div className="flex flex-wrap gap-2">
            {acciones.asumir && (
              <button
                type="button"
                disabled={guardando}
                onClick={() => ejecutar(() => alertasApi.asumir(idAlerta))}
                className={CLASE_BOTON_SECUNDARIO}
              >
                Asumir el caso
              </button>
            )}
            {acciones.resolver && (
              <button
                type="button"
                disabled={guardando}
                onClick={() => {
                  setErrorAccion(null);
                  setResolviendo(true);
                }}
                className={CLASE_BOTON_PRINCIPAL}
              >
                Resolver alerta
              </button>
            )}
            {acciones.cerrarSeguimiento && (
              <button type="button" disabled={guardando} onClick={cerrarSeguimiento} className={CLASE_BOTON_PRINCIPAL}>
                Cerrar seguimiento
              </button>
            )}
          </div>
        )}
      </div>

      {errorAccion && !resolviendo && (
        <p role="alert" className={CLASE_ERROR}>
          {errorAccion}
        </p>
      )}

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        <div className="space-y-6">
          <section className={CLASE_TARJETA}>
            <h2 className="mb-3 text-lg font-medium text-tinta">Estado</h2>
            <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-1 text-sm">
              <dt className="text-slate-500">Atención</dt>
              <dd className="text-tinta">{ESTADO[alerta.estadoAtencion]}</dd>
              <dt className="text-slate-500">Responsable</dt>
              <dd className="text-tinta">
                {alerta.responsable ?? <span className="text-slate-400">Sin asignar</span>}
              </dd>
              {esSalud && (
                <>
                  <dt className="text-slate-500">Contactos</dt>
                  <dd className={alerta.prioridadMaxima ? 'font-bold text-[#a1111a]' : 'text-tinta'}>
                    {describirContacto(alerta)}
                  </dd>
                </>
              )}
            </dl>

            {atencion && (
              <div className="mt-4 border-t border-slate-200 pt-4 text-sm">
                <p className="font-medium text-tinta">
                  {atencion.resolutor ? `Resuelta por ${atencion.resolutor}` : 'Cerrada por el sistema'} el{' '}
                  {formatearFechaHora(atencion.fechaHora)}
                </p>
                {atencion.clasificacion && <p className="text-tinta">{CLASIFICACION[atencion.clasificacion]}</p>}
                {atencion.observaciones && (
                  <p className="mt-1 whitespace-pre-line text-slate-600">{atencion.observaciones}</p>
                )}
              </div>
            )}
          </section>

          <section className={CLASE_TARJETA}>
            <h2 className="mb-3 text-lg font-medium text-tinta">Evidencia</h2>
            <EvidenciaDeAlerta
              evidencia={alerta.evidencia}
              discrepancias={alerta.discrepancias}
              tipoEvento={alerta.tipoEvento}
            />
          </section>
        </div>

        <section className={CLASE_TARJETA}>
          <h2 className="mb-3 text-lg font-medium text-tinta">Línea de tiempo</h2>
          <LineaTiempo pasos={alerta.lineaTiempo} />
        </section>
      </div>

      {resolviendo && (
        <DialogoResolver
          numero={alerta.numero}
          esSalud={esSalud}
          error={errorAccion}
          guardando={guardando}
          alResolver={resolver}
          alCancelar={() => {
            setResolviendo(false);
            setErrorAccion(null);
          }}
        />
      )}
    </div>
  );
}
