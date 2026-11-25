import { Link, useNavigate } from 'react-router';
import { formatearHoraReciente, hoyEnChile } from '../../comun/fechas';
import { Aviso, BannerAccion, Cargando, Vacio } from '../../comun/ui/Aviso';
import { Boton, claseBoton } from '../../comun/ui/Boton';
import { BarraLeyenda, ResumenAnillo, TiraMetricas } from '../../comun/ui/Metricas';
import { EncabezadoVista, Panel } from '../../comun/ui/Panel';
import { Paginacion, TABLA, Tabla, usePaginacion } from '../../comun/ui/Tabla';
import { useDatos } from '../../comun/useDatos';
import { alertasApi } from '../alertas/alertasApi';
import { CONDICION, InsigniaEstado, InsigniaGravedad } from '../alertas/etiquetas';
import type { ResumenAlerta } from '../alertas/tipos';
import { MOTIVO_SEMAFORO, NOMBRE_SEMAFORO, type ColorSemaforo } from '../relojes/tipos';
import { useSesion } from '../sesion/SesionContext';
import { panelApi, type UsuarioPanel } from './panelApi';

/** true si la alerta llegó hoy, en la hora de Chile. */
function esDeHoy(alerta: ResumenAlerta): boolean {
  return new Date(alerta.recepcion).toLocaleDateString('sv-SE', { timeZone: 'America/Santiago' }) === hoyEnChile();
}

/** @returns el porcentaje de parte sobre total, 0 si no hay total */
function porcentaje(parte: number, total: number): number {
  return total === 0 ? 0 : (parte / total) * 100;
}

/**
 * Portada "Monitoreo de alertas · Jornada actual" del mockup: lo que requiere acción ahora (sin asignar), los
 * totales del panel, cómo va la jornada (resolución y lectura) y la cola de alertas abiertas por urgencia. Sin
 * permiso para ver alertas, solo da la bienvenida.
 */
export function PaginaInicio() {
  const { usuario, puede } = useSesion();
  if (!usuario) return null;
  if (!puede('ALERTAS:VER')) {
    return (
      <>
        <EncabezadoVista titulo={`¡Hola, ${usuario.nombre.split(' ')[0]}!`} subtitulo="Te damos la bienvenida a AlertaCuida." />
        <Aviso>Usa el menú de arriba para ir a las secciones de tu rol.</Aviso>
      </>
    );
  }
  return <Monitoreo />;
}

/** El monitoreo de la jornada, para quien ve las alertas. */
function Monitoreo() {
  const navegar = useNavigate();
  const alertas = useDatos(alertasApi.listar);
  const panel = useDatos(panelApi.obtener);
  // La lista ya viene por urgencia: primero las sin respuesta, después por gravedad y llegada
  const abiertas = (alertas.datos ?? []).filter((a) => a.estadoAtencion !== 'RESUELTA');
  const sinAsignar = abiertas.filter((a) => a.estadoAtencion === 'SIN_ASIGNAR');
  const deHoy = (alertas.datos ?? []).filter(esDeHoy);
  const cerradasHoy = deHoy.filter((a) => a.estadoAtencion === 'RESUELTA').length;
  const nuevasHoy = deHoy.filter((a) => a.estadoAtencion === 'SIN_ASIGNAR').length;
  const conReloj = (panel.datos?.usuarios ?? []).filter((u) => u.reloj);
  const relojesBien = conReloj.filter((u) => u.reloj?.semaforo === 'VERDE').length;

  return (
    <>
      <EncabezadoVista
        titulo="Monitoreo de alertas · Jornada actual"
        subtitulo="Estado de recepción y atención de eventos biométricos y caídas en la comuna de Coquimbo."
        acciones={
          <Link to="/alertas" className={claseBoton('normal')}>
            Ver bandeja completa →
          </Link>
        }
      />

      {alertas.error && <Aviso tono="error">{alertas.error}</Aviso>}

      {sinAsignar.length > 0 && (
        <BannerAccion
          accion={
            <Boton variante="peligro" onClick={() => navegar(`/alertas/${sinAsignar[0].id}`)}>
              Gestionar caso →
            </Boton>
          }
        >
          <b>
            {sinAsignar.length === 1 ? '1 alerta requiere' : `${sinAsignar.length} alertas requieren`} asignación
            inmediata
          </b>{' '}
          · {sinAsignar[0].paciente.nombres} {sinAsignar[0].paciente.apellidos} ({CONDICION[sinAsignar[0].tipoEvento]})
        </BannerAccion>
      )}

      <TiraMetricas
        metricas={[
          {
            titulo: 'Alertas activas',
            valor: panel.datos?.totales.alertasAbiertas ?? abiertas.length,
            nota: 'Requieren seguimiento',
          },
          { titulo: 'Pendientes de asignación', valor: sinAsignar.length, nota: 'Sin gestor/a asignado', destacada: sinAsignar.length > 0 },
          {
            titulo: 'Sin respuesta',
            valor: panel.datos?.totales.sinRespuesta ?? '—',
            nota: 'Nadie de la lista confirmó',
            destacada: (panel.datos?.totales.sinRespuesta ?? 0) > 0,
          },
          {
            titulo: 'Relojes funcionando',
            valor: panel.datos ? `${relojesBien} / ${conReloj.length}` : '—',
            nota: 'En verde, de los que están en uso',
          },
        ]}
      />

      <div className="mb-5 grid gap-4 md:grid-cols-2">
        <ResumenAnillo titulo="Estado de resolución" porcentaje={porcentaje(deHoy.length - cerradasHoy, deHoy.length)} etiqueta="Alertas de hoy en atención">
          <BarraLeyenda tono="teal" valor={deHoy.length - cerradasHoy} texto="En atención / proceso" />
          <BarraLeyenda tono="gris" valor={cerradasHoy} texto="Cerrados" />
          <p className="text-[11.5px] text-apagado">Alertas recibidas hoy.</p>
        </ResumenAnillo>
        <ResumenAnillo titulo="Estado de lectura" porcentaje={porcentaje(nuevasHoy, deHoy.length)} etiqueta="Alertas de hoy sin abrir">
          <BarraLeyenda tono="naranja" valor={nuevasHoy} texto="Nuevas sin asignar" />
          <BarraLeyenda tono="verde" valor={deHoy.length - nuevasHoy} texto="Revisadas por el equipo" />
          <p className="text-[11.5px] text-apagado">Alertas recibidas hoy.</p>
        </ResumenAnillo>
      </div>

      <Panel titulo="Cola prioritaria de alertas" className="mb-5">
        <p className="mb-3 text-[12.5px] text-apagado">Haz clic en cualquier fila para abrir el expediente del caso.</p>
        {alertas.cargando && !alertas.datos ? <Cargando /> : <ColaPrioritaria alertas={abiertas} />}
      </Panel>

      {panel.datos && <UsuariosConAlertas usuarios={panel.datos.usuarios.filter((u) => u.alertasAbiertas > 0)} />}
    </>
  );
}

/** Las alertas abiertas por urgencia, paginadas; cada fila abre la ficha. */
function ColaPrioritaria({ alertas }: { alertas: ResumenAlerta[] }) {
  const navegar = useNavigate();
  const paginacion = usePaginacion(alertas, 5);
  if (alertas.length === 0) return <Vacio>No hay alertas abiertas. ✓</Vacio>;
  return (
    <>
      <Tabla columnas={['Hora', 'Persona usuaria', 'Gravedad', 'Evento / condición', 'Estado', 'Encargado/a', null]} etiqueta="Cola prioritaria">
        {paginacion.visibles.map((a) => (
          <tr
            key={a.id}
            className={TABLA.filaClic}
            tabIndex={0}
            onClick={() => navegar(`/alertas/${a.id}`)}
            onKeyDown={(e) => e.key === 'Enter' && navegar(`/alertas/${a.id}`)}
          >
            <td className={`${TABLA.td} cifra whitespace-nowrap`}>{formatearHoraReciente(a.recepcion)}</td>
            <td className={TABLA.td}>
              <b className="text-navy">
                {a.paciente.nombres} {a.paciente.apellidos}
              </b>
              {a.paciente.edad !== null && <span className="text-apagado"> ({a.paciente.edad} a.)</span>}
            </td>
            <td className={TABLA.td}>
              <InsigniaGravedad severidad={a.severidad} />
            </td>
            <td className={TABLA.td}>
              {CONDICION[a.tipoEvento]}
              {a.prioridadMaxima && <span className="ml-1.5 text-xs font-bold text-magenta">· Sin respuesta</span>}
            </td>
            <td className={TABLA.td}>
              <InsigniaEstado estado={a.estadoAtencion} />
            </td>
            <td className={TABLA.td}>
              {a.responsable ?? <b className="text-magenta">Por asignar</b>}
            </td>
            <td className={`${TABLA.td} text-right`}>
              <span className={claseBoton('contorno', true)}>Gestionar</span>
            </td>
          </tr>
        ))}
      </Tabla>
      <Paginacion estado={paginacion} />
    </>
  );
}

/** Color del punto de cada semáforo. */
const PUNTO_SEMAFORO: Record<ColorSemaforo, string> = {
  VERDE: 'bg-verde',
  AMARILLO: 'bg-naranja',
  ROJO: 'bg-magenta',
  GRIS: 'bg-linea-oscura',
};

/** Usuarios con alertas abiertas y cómo está su reloj, ordenados por urgencia (del panel). */
function UsuariosConAlertas({ usuarios }: { usuarios: UsuarioPanel[] }) {
  const { puede } = useSesion();
  const paginacion = usePaginacion(usuarios, 5);
  if (usuarios.length === 0) return null;
  return (
    <Panel titulo="Usuarios con alertas abiertas">
      <Tabla columnas={['Persona usuaria', 'Alertas abiertas', 'Reloj', 'Batería']} etiqueta="Usuarios con alertas abiertas">
        {paginacion.visibles.map((u) => (
          <tr key={u.id} className={TABLA.fila}>
            <td className={TABLA.td}>
              {puede('USUARIOS:VER') ? (
                <Link to={`/usuarios/${u.id}`} className="font-bold text-navy hover:underline">
                  {u.nombres} {u.apellidos}
                </Link>
              ) : (
                <b className="text-navy">
                  {u.nombres} {u.apellidos}
                </b>
              )}
            </td>
            <td className={`${TABLA.td} cifra`}>{u.alertasAbiertas}</td>
            <td className={TABLA.td}>
              {u.reloj ? (
                <span className="inline-flex items-center gap-2" title={u.reloj.motivos.map((m) => MOTIVO_SEMAFORO[m]).join('\n')}>
                  <span aria-hidden="true" className={`h-2.5 w-2.5 rounded-full ${PUNTO_SEMAFORO[u.reloj.semaforo]}`} />
                  <span className="cifra text-[12px]">{u.reloj.identificador}</span>
                  <span className="text-apagado">· {NOMBRE_SEMAFORO[u.reloj.semaforo]}</span>
                </span>
              ) : (
                <span className="text-apagado">Sin reloj</span>
              )}
            </td>
            <td className={`${TABLA.td} cifra`}>{u.reloj?.nivelBateria != null ? `${u.reloj.nivelBateria} %` : '—'}</td>
          </tr>
        ))}
      </Tabla>
      <Paginacion estado={paginacion} />
    </Panel>
  );
}
