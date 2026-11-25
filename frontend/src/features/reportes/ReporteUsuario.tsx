import { useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router';
import { CLASE_CAMPO } from '../../comun/estilos';
import { formatearFecha, formatearFechaHora, hoyEnChile } from '../../comun/fechas';
import { Aviso, Cargando, Vacio } from '../../comun/ui/Aviso';
import { Boton, claseBoton } from '../../comun/ui/Boton';
import { BarraLeyenda, TiraMetricas } from '../../comun/ui/Metricas';
import { EncabezadoVista, Panel } from '../../comun/ui/Panel';
import { Segmentado } from '../../comun/ui/Pestanas';
import { TABLA, Tabla } from '../../comun/ui/Tabla';
import { useDatos } from '../../comun/useDatos';
import { CONDICION, GRAVEDAD, InsigniaGravedad } from '../alertas/etiquetas';
import type { Severidad } from '../alertas/tipos';
import { pacientesApi } from '../pacientes/pacientesApi';
import type { Umbral } from '../pacientes/tipos';
import { useSesion } from '../sesion/SesionContext';
import { BotonesExportar } from './BotonesExportar';
import { PERIODOS, type Periodo } from './periodos';
import { reportesApi, type Estadistica, type IndicadoresPeriodo, type MarcaAlerta, type PuntoTendencia } from './reportesApi';

/** @returns la fecha "AAAA-MM-DD" movida n días */
function sumarDias(fecha: string, dias: number): string {
  const d = new Date(`${fecha}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + dias);
  return d.toISOString().slice(0, 10);
}

/**
 * Reporte diario o semanal de un usuario (/reportes/:id/:periodo?dia=), como el reporte individual del mockup:
 * indicadores comparados con el período anterior, curvas de FC y SpO2 con la banda normal del usuario y las
 * alertas marcadas, y el historial de alertas del período. Se exporta a Excel o PDF.
 */
export function ReporteUsuario() {
  const { id = '', periodo = '' } = useParams();
  const navegar = useNavigate();
  const [dia, setDia] = useState(hoyEnChile());
  const actual = PERIODOS.find((p) => p.valor === periodo);
  const detalle = useDatos(() => pacientesApi.detalle(id), id);
  const reporte = useDatos(
    () => (actual ? reportesApi.reporte(id, actual.valor, dia) : Promise.reject(new Error('período'))),
    `${id}-${periodo}-${dia}`,
  );

  if (!actual) return <Navigate to="/reportes" replace />;
  if (detalle.cargando && !detalle.datos) return <Cargando />;
  if (detalle.error || !detalle.datos) return <Aviso tono="error">{detalle.error ?? 'No se encontró el usuario.'}</Aviso>;

  const { paciente, edad } = detalle.datos;
  const nombre = `${paciente.datos.nombres} ${paciente.datos.apellidos}`;
  const paso = actual.valor === 'diario' ? 1 : 7;
  const hoy = hoyEnChile();

  return (
    <>
      <EncabezadoVista
        migas={[
          { texto: 'Reportería', a: '/reportes' },
          { texto: nombre, a: `/reportes/${paciente.id}/${actual.valor}` },
        ]}
        titulo={`Reporte ${actual.etiqueta.toLowerCase()} · ${nombre}`}
        subtitulo={`${edad !== null ? `${edad} años · ` : ''}Signos vitales, alertas y uso del reloj del período.`}
        acciones={
          <>
            <BotonesExportar exportar={(formato) => reportesApi.exportarUsuario(paciente.id, actual.valor, dia, formato)} />
            <Link to={`/usuarios/${paciente.id}`} className={claseBoton('contorno', true)}>
              Ficha del usuario
            </Link>
          </>
        }
      />

      <div className="mb-5 flex flex-wrap items-end gap-3">
        <Segmentado
          etiqueta="Período del reporte"
          opciones={PERIODOS.map(({ valor, etiqueta }) => ({ clave: valor, texto: etiqueta }))}
          activa={actual.valor}
          alCambiar={(valor: Periodo) => navegar(`/reportes/${paciente.id}/${valor}`)}
        />
        <div className="flex items-center gap-1.5">
          <Boton variante="contorno" chico onClick={() => setDia(sumarDias(dia, -paso))} aria-label="Período anterior">
            ‹
          </Boton>
          <input
            type="date"
            value={dia}
            max={hoy}
            onChange={(e) => e.target.value && setDia(e.target.value)}
            aria-label={actual.valor === 'diario' ? 'Día del reporte' : 'Un día de la semana del reporte'}
            className={`${CLASE_CAMPO} mt-0 w-40`}
          />
          <Boton
            variante="contorno"
            chico
            disabled={sumarDias(dia, paso) > hoy}
            onClick={() => setDia(sumarDias(dia, paso))}
            aria-label="Período siguiente"
          >
            ›
          </Boton>
        </div>
      </div>

      {reporte.error && <Aviso tono="error">{reporte.error}</Aviso>}
      {reporte.cargando && !reporte.datos && <Cargando />}
      {reporte.datos && (
        <Contenido
          idPaciente={paciente.id}
          desde={reporte.datos.desde}
          hasta={reporte.datos.hasta}
          indicadores={reporte.datos.indicadores}
          anterior={reporte.datos.anterior}
          semanal={actual.valor === 'semanal'}
        />
      )}
    </>
  );
}

/** Indicadores, curvas e historial de un período ya cargado. */
function Contenido({
  idPaciente,
  desde,
  hasta,
  indicadores,
  anterior,
  semanal,
}: {
  idPaciente: string;
  desde: string;
  hasta: string;
  indicadores: IndicadoresPeriodo;
  anterior: IndicadoresPeriodo | null;
  semanal: boolean;
}) {
  const { puede } = useSesion();
  const tendencias = useDatos(() => reportesApi.tendencias(idPaciente, desde, hasta), `${idPaciente}-${desde}-${hasta}`);
  const perfil = useDatos(
    () => (puede('USUARIOS:VER') ? pacientesApi.perfilClinico(idPaciente) : Promise.resolve(null)),
    idPaciente,
  );
  const total = totalAlertas(indicadores);
  const comparacion = semanal ? 'vs. semana anterior' : 'vs. día anterior';

  return (
    <>
      <p className="mb-3 text-[12.5px] text-apagado">
        Período: <span className="cifra">{formatearFecha(desde)}</span>
        {desde !== hasta && (
          <>
            {' '}
            al <span className="cifra">{formatearFecha(hasta)}</span>
          </>
        )}
      </p>
      <TiraMetricas
        metricas={[
          {
            titulo: 'FC promedio',
            valor: valorEstadistica(indicadores.fc, ' lpm'),
            nota: rango(indicadores.fc) ?? 'Sin lecturas',
          },
          {
            titulo: 'SpO2 promedio',
            valor: valorEstadistica(indicadores.spo2, ' %'),
            nota: rango(indicadores.spo2) ?? 'Sin lecturas',
          },
          {
            titulo: 'Alertas del período',
            valor: total,
            destacada: (indicadores.alertasPorGravedad.GRAVE ?? 0) > 0,
            nota: anterior ? `${diferencia(total, totalAlertas(anterior))} ${comparacion}` : `${indicadores.alertasCanceladas} canceladas por el usuario`,
          },
          {
            titulo: 'Uso del reloj',
            valor: `${(indicadores.uso.minutosPuesto / 60).toFixed(1)} h`,
            nota: `Batería mínima ${indicadores.bateriaMinima ?? '—'} % · ${indicadores.uso.sincronizaciones} sincronizaciones`,
          },
        ]}
      />

      {tendencias.error && <Aviso tono="error">{tendencias.error}</Aviso>}
      <div className="grid items-start gap-5 xl:grid-cols-2">
        <Panel titulo="Frecuencia cardíaca (lpm)">
          {tendencias.datos ? (
            <Curva
              puntos={tendencias.datos.fc}
              alertas={tendencias.datos.alertas}
              banda={perfil.datos?.fc.umbral ?? null}
              desde={tendencias.datos.desde}
              hasta={tendencias.datos.hasta}
              unidad="lpm"
            />
          ) : (
            <Cargando />
          )}
        </Panel>
        <Panel titulo="Saturación de oxígeno (SpO2 %)">
          {tendencias.datos ? (
            <Curva
              puntos={tendencias.datos.spo2}
              alertas={tendencias.datos.alertas}
              banda={perfil.datos?.spo2.umbral ?? null}
              desde={tendencias.datos.desde}
              hasta={tendencias.datos.hasta}
              unidad="%"
            />
          ) : (
            <Cargando />
          )}
        </Panel>
      </div>

      <div className="mt-5 grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)]">
        <Panel titulo="Alertas por gravedad">
          <div className="grid gap-2">
            {(['GRAVE', 'MEDIA', 'LEVE'] as Severidad[]).map((g) => (
              <BarraLeyenda
                key={g}
                tono={g === 'GRAVE' ? 'magenta' : g === 'MEDIA' ? 'naranja' : 'verde'}
                valor={indicadores.alertasPorGravedad[g] ?? 0}
                texto={GRAVEDAD[g].texto.toLowerCase()}
                total={total}
              />
            ))}
            <BarraLeyenda tono="gris" valor={indicadores.alertasCanceladas} texto="canceladas por el usuario" />
          </div>
        </Panel>
        <Panel titulo="Historial de alertas del período">
          {tendencias.datos && <Historial alertas={tendencias.datos.alertas} />}
        </Panel>
      </div>
    </>
  );
}

/** @returns el total de alertas del período */
function totalAlertas(indicadores: IndicadoresPeriodo): number {
  return Object.values(indicadores.alertasPorGravedad).reduce((suma, n) => suma + (n ?? 0), 0);
}

/** @returns "+2", "−1" o "igual" */
function diferencia(actual: number, anterior: number): string {
  const d = actual - anterior;
  return d === 0 ? 'igual' : d > 0 ? `+${d}` : `−${-d}`;
}

/** @returns el promedio redondeado con su unidad, o "—" */
function valorEstadistica(e: Estadistica, unidad: string): string {
  return e.promedio === null ? '—' : `${Math.round(e.promedio)}${unidad}`;
}

/** @returns "mín 58 · máx 112" o null si no hubo lecturas */
function rango(e: Estadistica): string | null {
  if (e.promedio === null || e.minimo === null || e.maximo === null) return null;
  return `mín ${Math.round(e.minimo)} · máx ${Math.round(e.maximo)} · ${e.lecturas} lecturas`;
}

/** Medidas del gráfico (unidades del viewBox). */
const ANCHO = 640;
const ALTO = 220;
const MARGEN = { izquierda: 36, derecha: 10, arriba: 12, abajo: 26 };

/** Color de la marca de cada gravedad. */
const COLOR_GRAVEDAD: Record<Severidad, string> = {
  GRAVE: 'var(--color-magenta)',
  MEDIA: 'var(--color-naranja)',
  LEVE: 'var(--color-verde)',
};

/**
 * Curva del promedio con el rango mín–máx sombreado, la banda normal del usuario (si se puede ver su perfil
 * clínico) y una línea vertical por cada alerta del período.
 */
function Curva({
  puntos,
  alertas,
  banda,
  desde,
  hasta,
  unidad,
}: {
  puntos: PuntoTendencia[];
  alertas: MarcaAlerta[];
  banda: Umbral | null;
  desde: string;
  hasta: string;
  unidad: string;
}) {
  if (puntos.length === 0) return <Vacio>Sin lecturas en el período.</Vacio>;

  const t0 = Date.parse(desde);
  const t1 = Math.max(Date.parse(hasta), t0 + 1);
  const valores = puntos.flatMap((p) => [p.minimo, p.maximo]);
  if (banda) valores.push(banda.normalMin, banda.normalMax ?? banda.normalMin);
  const bajo = Math.floor((Math.min(...valores) - 5) / 10) * 10;
  const alto = Math.ceil((Math.max(...valores) + 5) / 10) * 10;
  const x = (iso: string) =>
    MARGEN.izquierda + ((Date.parse(iso) - t0) / (t1 - t0)) * (ANCHO - MARGEN.izquierda - MARGEN.derecha);
  const y = (v: number) => MARGEN.arriba + ((alto - v) / (alto - bajo)) * (ALTO - MARGEN.arriba - MARGEN.abajo);
  const linea = puntos.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(p.fechaHora).toFixed(1)},${y(p.promedio).toFixed(1)}`).join(' ');
  const area =
    puntos.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(p.fechaHora).toFixed(1)},${y(p.maximo).toFixed(1)}`).join(' ') +
    ' ' +
    [...puntos].reverse().map((p) => `L${x(p.fechaHora).toFixed(1)},${y(p.minimo).toFixed(1)}`).join(' ') +
    ' Z';
  const marcas = Array.from({ length: 5 }, (_, i) => bajo + ((alto - bajo) * i) / 4);
  const etiquetasX = [desde, new Date((t0 + t1) / 2).toISOString(), hasta];

  return (
    <figure>
      <svg viewBox={`0 0 ${ANCHO} ${ALTO}`} className="h-auto w-full" role="img" aria-label={`Curva de ${unidad} del período`}>
        {marcas.map((v) => (
          <g key={v}>
            <line x1={MARGEN.izquierda} x2={ANCHO - MARGEN.derecha} y1={y(v)} y2={y(v)} stroke="var(--color-linea)" />
            <text x={MARGEN.izquierda - 6} y={y(v) + 3} textAnchor="end" fontSize="10" fill="var(--color-apagado)" className="cifra">
              {Math.round(v)}
            </text>
          </g>
        ))}
        {banda && (
          <rect
            x={MARGEN.izquierda}
            width={ANCHO - MARGEN.izquierda - MARGEN.derecha}
            y={y(banda.normalMax ?? alto)}
            height={Math.max(0, y(banda.normalMin) - y(banda.normalMax ?? alto))}
            fill="var(--color-verde)"
            opacity="0.08"
          />
        )}
        <path d={area} fill="var(--color-teal)" opacity="0.15" />
        <path d={linea} fill="none" stroke="var(--color-teal)" strokeWidth="2" strokeLinejoin="round" />
        {alertas.map((a) => (
          <line
            key={a.id}
            x1={x(a.fechaHoraDeteccion)}
            x2={x(a.fechaHoraDeteccion)}
            y1={MARGEN.arriba}
            y2={ALTO - MARGEN.abajo}
            stroke={COLOR_GRAVEDAD[a.severidad]}
            strokeDasharray="3 3"
            strokeWidth="1.5"
          >
            <title>
              Alerta #{a.numero} · {CONDICION[a.tipoEvento]} · {formatearFechaHora(a.fechaHoraDeteccion)}
            </title>
          </line>
        ))}
        {etiquetasX.map((iso, i) => (
          <text
            key={iso}
            x={x(iso)}
            y={ALTO - 8}
            textAnchor={i === 0 ? 'start' : i === 2 ? 'end' : 'middle'}
            fontSize="10"
            fill="var(--color-apagado)"
          >
            {formatearFechaHora(iso)}
          </text>
        ))}
      </svg>
      <figcaption className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-[11.5px] text-apagado">
        <span className="flex items-center gap-1.5">
          <span aria-hidden="true" className="h-0.5 w-4 bg-teal" /> Promedio
        </span>
        <span className="flex items-center gap-1.5">
          <span aria-hidden="true" className="h-2.5 w-4 bg-teal/20" /> Mínimo a máximo
        </span>
        {banda && (
          <span className="flex items-center gap-1.5">
            <span aria-hidden="true" className="h-2.5 w-4 bg-verde/15" /> Rango normal del usuario ({banda.normalMin}
            {banda.normalMax !== null ? `–${banda.normalMax}` : ' o más'} {unidad})
          </span>
        )}
        {alertas.length > 0 && (
          <span className="flex items-center gap-1.5">
            <span aria-hidden="true" className="h-3 w-0 border-l-2 border-dashed border-magenta" /> Alertas
          </span>
        )}
      </figcaption>
    </figure>
  );
}

/** Tabla de las alertas del período, con enlace a su ficha si se pueden ver. */
function Historial({ alertas }: { alertas: MarcaAlerta[] }) {
  const { puede } = useSesion();
  if (alertas.length === 0) return <Vacio>Sin alertas en el período.</Vacio>;
  const ordenadas = [...alertas].sort((a, b) => b.fechaHoraDeteccion.localeCompare(a.fechaHoraDeteccion));

  return (
    <Tabla columnas={['Folio', 'Detectada', 'Evento', 'Gravedad', null]} etiqueta="Alertas del período">
      {ordenadas.map((a) => (
        <tr key={a.id} className={TABLA.fila}>
          <td className={`${TABLA.td} cifra`}>
            {puede('ALERTAS:VER') ? (
              <Link to={`/alertas/${a.id}`} className="font-bold text-teal-oscuro hover:underline">
                #{a.numero}
              </Link>
            ) : (
              `#${a.numero}`
            )}
          </td>
          <td className={`${TABLA.td} cifra whitespace-nowrap`}>{formatearFechaHora(a.fechaHoraDeteccion)}</td>
          <td className={TABLA.td}>{CONDICION[a.tipoEvento]}</td>
          <td className={TABLA.td}>
            <InsigniaGravedad severidad={a.severidad} />
          </td>
          <td className={`${TABLA.td} text-[12px] text-apagado`}>{a.cancelada ? 'Cancelada por el usuario' : ''}</td>
        </tr>
      ))}
    </Tabla>
  );
}
