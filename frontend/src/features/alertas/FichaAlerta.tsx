import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { ErrorApi } from '../../api/cliente';
import { useConfirmar } from '../../comun/Confirmacion';
import { formatearAntiguedad, formatearFechaHora } from '../../comun/fechas';
import { Aviso, Cargando } from '../../comun/ui/Aviso';
import { Boton } from '../../comun/ui/Boton';
import { EncabezadoVista, ListaDatos, Panel } from '../../comun/ui/Panel';
import { Segmentado } from '../../comun/ui/Pestanas';
import { useToast } from '../../comun/ui/Toast';
import { useDatos } from '../../comun/useDatos';
import { useSesion } from '../sesion/SesionContext';
import { alertasApi } from './alertasApi';
import { DialogoResolver } from './DialogoResolver';
import { CLASIFICACION, CONDICION, describirContacto, ESTADO, InsigniaGravedad, InsigniaSimulacro } from './etiquetas';
import { EvidenciaDeAlerta } from './EvidenciaDeAlerta';
import { LineaTiempo } from './LineaTiempo';
import { RegistrarGestion } from './RegistrarGestion';
import type { ClasificacionAlerta, DetalleAlerta, EstadoAtencion } from './tipos';

/** @returns el mensaje del backend, o uno genérico si no hubo conexión */
function mensajeDe(e: unknown): string {
  return e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.';
}

/** Cada cuánto se vuelve a pedir la ficha, para ver los avisos y el seguimiento nuevos sin refrescar. */
const ACTUALIZAR_CADA_MS = 15_000;

/** Los pasos del flujo de atención, en orden. */
const FLUJO: EstadoAtencion[] = ['SIN_ASIGNAR', 'EN_ATENCION', 'EN_SEGUIMIENTO', 'RESUELTA'];

/** Qué acciones admite la alerta en su estado actual (el backend igual las valida). */
function accionesPosibles(alerta: DetalleAlerta) {
  const cancelada = alerta.estadoContacto === 'CANCELADA';
  const abierta = alerta.estadoAtencion === 'SIN_ASIGNAR' || alerta.estadoAtencion === 'EN_ATENCION';
  const { atencion } = alerta;
  return {
    asumir: !cancelada && alerta.estadoAtencion === 'SIN_ASIGNAR',
    resolver: !cancelada && abierta,
    cerrarSeguimiento: alerta.estadoAtencion === 'EN_SEGUIMIENTO',
    // La cerró el sistema al llegar un contacto: falta que el equipo la clasifique (24-11-2026)
    completarCierre:
      !cancelada &&
      alerta.categoria === 'SALUD' &&
      atencion !== null &&
      atencion.resolutor === null &&
      atencion.clasificacion === null,
    // Gestiones en la bitácora: mientras no esté cerrada del todo ni cancelada
    gestion: !cancelada && alerta.estadoAtencion !== 'RESUELTA',
  };
}

/** @returns el contacto cuya llegada resolvió la alerta, según su línea de tiempo; null si no fue así */
function quienLlego(alerta: DetalleAlerta): string | null {
  return alerta.lineaTiempo.find((paso) => paso.tipo === 'ALERTA_RESUELTA')?.contacto ?? null;
}

/**
 * Expediente de una alerta (/alertas/:id), con el diseño del mockup: quién es, el flujo de atención, la
 * telemetría que explica la alerta y la bitácora del caso (línea de tiempo y gestiones del equipo). Con
 * ALERTAS:EDITAR se puede asumir, resolver, completar el cierre, cerrar el seguimiento y registrar gestiones.
 */
export function FichaAlerta() {
  const { id = '' } = useParams();
  const { puede } = useSesion();
  const confirmar = useConfirmar();
  const avisar = useToast();
  const { datos: alerta, error, cargando, recargar } = useDatos(() => alertasApi.detalle(id), id, {
    cadaMs: ACTUALIZAR_CADA_MS,
  });
  const [errorAccion, setErrorAccion] = useState<string | null>(null);
  const [resolviendo, setResolviendo] = useState(false);
  const [completando, setCompletando] = useState(false);
  const [guardando, setGuardando] = useState(false);

  if (cargando && !alerta) return <Cargando />;
  if (error || !alerta) return <Aviso tono="error">{error ?? 'No se encontró la alerta.'}</Aviso>;

  const acciones = puede('ALERTAS:EDITAR') ? accionesPosibles(alerta) : null;
  const esSalud = alerta.categoria === 'SALUD';
  const idAlerta = alerta.id;
  const paso = FLUJO.indexOf(alerta.estadoAtencion);

  /** Ejecuta una acción, muestra su error si falla y recarga la ficha. */
  async function ejecutar(accion: () => Promise<void>, exito?: string): Promise<boolean> {
    setGuardando(true);
    setErrorAccion(null);
    try {
      await accion();
      if (exito) avisar(exito);
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
    const guardar = completando ? alertasApi.completarCierre : alertasApi.resolver;
    const exito = completando ? 'Cierre completado' : 'Alerta resuelta';
    if (await ejecutar(() => guardar(idAlerta, clasificacion, observaciones), exito)) {
      setResolviendo(false);
      setCompletando(false);
    }
  }

  /** Pide confirmación y cierra el seguimiento. */
  async function cerrarSeguimiento() {
    const confirmado = await confirmar({
      titulo: '¿Cerrar el seguimiento?',
      mensaje: 'La alerta quedará cerrada.',
      textoConfirmar: 'Cerrar seguimiento',
    });
    if (confirmado) await ejecutar(() => alertasApi.cerrarSeguimiento(idAlerta), 'Seguimiento cerrado');
  }

  const { paciente, atencion } = alerta;
  const nombre = `${paciente.nombres} ${paciente.apellidos}`;

  return (
    <>
      <EncabezadoVista
        migas={[{ texto: 'Alertas', a: '/alertas' }]}
        titulo={
          <>
            {puede('USUARIOS:VER') ? (
              <Link to={`/usuarios/${paciente.id}`} className="hover:underline">
                {nombre}
              </Link>
            ) : (
              nombre
            )}
            {paciente.edad !== null && `, ${paciente.edad} años`}
          </>
        }
        subtitulo={
          <>
            Expediente de alerta <span className="cifra">#{alerta.numero}</span> · {CONDICION[alerta.tipoEvento]} ·
            recibida el {formatearFechaHora(alerta.recepcion)} ({formatearAntiguedad(alerta.recepcion)})
          </>
        }
        acciones={
          <div className="flex items-center gap-1.5">
            <InsigniaGravedad severidad={alerta.severidad} />
            {alerta.simulacro && <InsigniaSimulacro />}
          </div>
        }
      />

      <Panel titulo="Flujo de atención del caso" className="mb-5">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <Segmentado
            etiqueta="Estado de atención"
            activa={alerta.estadoAtencion}
            hechas={FLUJO.filter((_, i) => i < paso)}
            opciones={FLUJO.map((estado) => ({ clave: estado, texto: ESTADO[estado] }))}
          />
          {acciones && (
            <div className="flex flex-wrap gap-2">
              {acciones.asumir && (
                <Boton variante="contorno" disabled={guardando} onClick={() => ejecutar(() => alertasApi.asumir(idAlerta), 'Asumiste el caso')}>
                  Asumir el caso
                </Boton>
              )}
              {acciones.resolver && (
                <Boton
                  variante="principal"
                  disabled={guardando}
                  onClick={() => {
                    setErrorAccion(null);
                    setResolviendo(true);
                  }}
                >
                  Resolver alerta
                </Boton>
              )}
              {acciones.completarCierre && (
                <Boton
                  variante="principal"
                  disabled={guardando}
                  onClick={() => {
                    setErrorAccion(null);
                    setCompletando(true);
                    setResolviendo(true);
                  }}
                >
                  Completar cierre
                </Boton>
              )}
              {acciones.cerrarSeguimiento && (
                <Boton variante="principal" disabled={guardando} onClick={cerrarSeguimiento}>
                  Cerrar seguimiento
                </Boton>
              )}
            </div>
          )}
        </div>

        {errorAccion && !resolviendo && (
          <Aviso tono="error" className="mt-3">
            {errorAccion}
          </Aviso>
        )}

        <div className="mt-4 border-t border-linea pt-4">
          <ListaDatos
            datos={[
              ['Gestor/a responsable', alerta.responsable ?? <b className="text-magenta">Por asignar</b>],
              ...(esSalud
                ? ([
                    [
                      'Contactos del usuario',
                      <span key="c" className={alerta.prioridadMaxima ? 'font-bold text-magenta' : ''}>
                        {describirContacto(alerta)}
                      </span>,
                    ],
                  ] as [string, React.ReactNode][])
                : []),
            ]}
          />
        </div>

        {atencion && (
          <div className="mt-4 rounded-panel border border-verde-borde bg-verde-tinte px-4 py-3 text-[13px]">
            <p className="font-bold text-verde-texto">
              {atencion.resolutor
                ? `Resuelta por ${atencion.resolutor}`
                : quienLlego(alerta)
                  ? `Resuelta al llegar ${quienLlego(alerta)} donde el usuario`
                  : 'Cerrada por el sistema'}{' '}
              el {formatearFechaHora(atencion.fechaHora)}
            </p>
            {atencion.clasificacion && <p className="text-navy">{CLASIFICACION[atencion.clasificacion]}</p>}
            {atencion.observaciones && <p className="mt-1 whitespace-pre-line text-texto">{atencion.observaciones}</p>}
          </div>
        )}
      </Panel>

      <div className="grid gap-5 lg:grid-cols-[minmax(0,1.25fr)_minmax(0,1fr)]">
        <Panel titulo="Telemetría y evidencia">
          <EvidenciaDeAlerta evidencia={alerta.evidencia} discrepancias={alerta.discrepancias} tipoEvento={alerta.tipoEvento} />
        </Panel>

        <Panel titulo="Bitácora del caso">
          {acciones?.gestion && <RegistrarGestion idAlerta={idAlerta} alGuardar={recargar} />}
          <LineaTiempo pasos={alerta.lineaTiempo} />
        </Panel>
      </div>

      {resolviendo && (
        <DialogoResolver
          numero={alerta.numero}
          esSalud={esSalud}
          error={errorAccion}
          guardando={guardando}
          alResolver={resolver}
          completando={completando}
          alCancelar={() => {
            setResolviendo(false);
            setCompletando(false);
            setErrorAccion(null);
          }}
        />
      )}
    </>
  );
}
