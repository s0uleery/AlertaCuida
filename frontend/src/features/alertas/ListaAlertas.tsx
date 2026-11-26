import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { CLASE_CAMPO, CLASE_ETIQUETA } from '../../comun/estilos';
import { formatearAntiguedad, formatearHoraReciente } from '../../comun/fechas';
import { Aviso, Cargando, Vacio } from '../../comun/ui/Aviso';
import { Boton } from '../../comun/ui/Boton';
import { EncabezadoVista } from '../../comun/ui/Panel';
import { Pestanas } from '../../comun/ui/Pestanas';
import { Paginacion, TABLA, Tabla, usePaginacion } from '../../comun/ui/Tabla';
import { useDatos } from '../../comun/useDatos';
import { alertasApi } from './alertasApi';
import { CONDICION, describirContacto, ESTADO, InsigniaEstado, InsigniaGravedad, InsigniaSimulacro } from './etiquetas';
import type { CategoriaAlerta, EstadoAtencion, ResumenAlerta, Severidad } from './tipos';

/** Qué alertas se muestran según su atención. */
type FiltroEstado = 'ABIERTAS' | 'CERRADAS' | 'TODAS';

/** Borde de la fila: las graves y medias se destacan; las leves no. */
const BORDE_GRAVEDAD: Record<Severidad, string> = {
  GRAVE: 'border-l-[3px] border-l-magenta',
  MEDIA: 'border-l-[3px] border-l-naranja',
  LEVE: 'border-l-[3px] border-l-transparent',
};

/** true si el equipo todavía no la cierra (en seguimiento cuenta como abierta). */
function estaAbierta(alerta: ResumenAlerta): boolean {
  return alerta.estadoAtencion !== 'RESUELTA';
}

/** true si el texto buscado está en el nombre del usuario, la condición o el folio (#142 o 142). */
function coincide(alerta: ResumenAlerta, busqueda: string): boolean {
  const texto = `${alerta.paciente.nombres} ${alerta.paciente.apellidos} #${alerta.numero} ${CONDICION[alerta.tipoEvento]}`;
  return normalizar(texto).includes(normalizar(busqueda));
}

/** @returns el texto en minúsculas y sin tildes, para buscar */
function normalizar(texto: string): string {
  return texto.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase();
}

/**
 * Bandeja de alertas del mockup, en pestañas Salud y Reloj. Por defecto muestra las abiertas, en el orden del
 * backend: primero las que nadie respondió y después por gravedad y llegada. Se filtra por texto, gravedad y
 * estado de atención. Los simulacros aparecen con su etiqueta y las canceladas por el usuario no aparecen.
 */
export function ListaAlertas() {
  const navegar = useNavigate();
  // Se vuelve a pedir cada 15 s para ver las alertas nuevas sin refrescar la página
  const { datos, error, cargando } = useDatos(alertasApi.listar, '', { cadaMs: 15_000 });
  const [pestana, setPestana] = useState<CategoriaAlerta>('SALUD');
  const [filtro, setFiltro] = useState<FiltroEstado>('ABIERTAS');
  const [gravedad, setGravedad] = useState<Severidad | ''>('');
  const [atencion, setAtencion] = useState<EstadoAtencion | ''>('');
  const [busqueda, setBusqueda] = useState('');

  const alertas = datos ?? [];
  const abiertasPorPestana = (categoria: CategoriaAlerta) =>
    alertas.filter((a) => a.categoria === categoria && estaAbierta(a)).length;

  const visibles = useMemo(
    () =>
      (datos ?? []).filter(
        (a) =>
          a.categoria === pestana &&
          (filtro === 'TODAS' || (filtro === 'ABIERTAS') === estaAbierta(a)) &&
          (!gravedad || a.severidad === gravedad) &&
          (!atencion || a.estadoAtencion === atencion) &&
          (!busqueda.trim() || coincide(a, busqueda.trim())),
      ),
    [datos, pestana, filtro, gravedad, atencion, busqueda],
  );
  const paginacion = usePaginacion(visibles, 10);
  const esSalud = pestana === 'SALUD';
  const hayFiltros = busqueda !== '' || gravedad !== '' || atencion !== '' || filtro !== 'ABIERTAS';

  /** Vuelve a los filtros iniciales. */
  function limpiar() {
    setBusqueda('');
    setGravedad('');
    setAtencion('');
    setFiltro('ABIERTAS');
  }

  return (
    <>
      <EncabezadoVista
        titulo="Bandeja de alertas"
        subtitulo="Eventos automáticos emitidos por los dispositivos de teleasistencia. Las que nadie respondió van primero."
      />

      <Pestanas
        etiqueta="Tipo de alerta"
        activa={pestana}
        alCambiar={setPestana}
        pestanas={[
          { clave: 'SALUD', texto: <>Salud <Contador n={abiertasPorPestana('SALUD')} /></> },
          { clave: 'TECNICA', texto: <>Reloj <Contador n={abiertasPorPestana('TECNICA')} /></> },
        ]}
      />

      <div className="mb-4 flex flex-wrap items-end gap-3">
        <label className="block min-w-56 flex-1">
          <span className={CLASE_ETIQUETA}>Buscar persona usuaria, evento o folio</span>
          <input
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            placeholder="Ej: María Soto, Caída, 142…"
            className={CLASE_CAMPO}
          />
        </label>
        <label className="block w-40">
          <span className={CLASE_ETIQUETA}>Gravedad</span>
          <select value={gravedad} onChange={(e) => setGravedad(e.target.value as Severidad | '')} className={CLASE_CAMPO}>
            <option value="">Todas</option>
            <option value="GRAVE">Grave</option>
            <option value="MEDIA">Media</option>
            <option value="LEVE">Leve</option>
          </select>
        </label>
        <label className="block w-44">
          <span className={CLASE_ETIQUETA}>Estado de atención</span>
          <select value={atencion} onChange={(e) => setAtencion(e.target.value as EstadoAtencion | '')} className={CLASE_CAMPO}>
            <option value="">Todos</option>
            {(Object.keys(ESTADO) as EstadoAtencion[]).map((estado) => (
              <option key={estado} value={estado}>
                {ESTADO[estado]}
              </option>
            ))}
          </select>
        </label>
        <label className="block w-36">
          <span className={CLASE_ETIQUETA}>Mostrar</span>
          <select value={filtro} onChange={(e) => setFiltro(e.target.value as FiltroEstado)} className={CLASE_CAMPO}>
            <option value="ABIERTAS">Abiertas</option>
            <option value="CERRADAS">Cerradas</option>
            <option value="TODAS">Todas</option>
          </select>
        </label>
        <Boton variante="contorno" onClick={limpiar} disabled={!hayFiltros}>
          Limpiar filtros
        </Boton>
      </div>

      {error && <Aviso tono="error" className="mb-4">{error}</Aviso>}
      <p className="mb-2 text-[12.5px] text-apagado" aria-live="polite">
        <b className="cifra text-navy">{visibles.length}</b> {visibles.length === 1 ? 'alerta encontrada' : 'alertas encontradas'}
      </p>

      {cargando && !datos ? (
        <Cargando />
      ) : visibles.length === 0 ? (
        <Vacio>
          {hayFiltros ? 'No hay alertas que coincidan con los filtros seleccionados.' : 'No hay alertas abiertas en esta pestaña.'}
        </Vacio>
      ) : (
        <>
          <Tabla
            etiqueta="Alertas"
            columnas={[
              'Folio',
              'Hora',
              'Persona usuaria',
              'Gravedad',
              esSalud ? 'Evento / condición' : 'Problema del reloj',
              ...(esSalud ? ['Contactos'] : []),
              'Estado',
              'Encargado/a',
            ]}
          >
            {paginacion.visibles.map((alerta) => (
              <tr
                key={alerta.id}
                tabIndex={0}
                onClick={() => navegar(`/alertas/${alerta.id}`)}
                onKeyDown={(e) => e.key === 'Enter' && navegar(`/alertas/${alerta.id}`)}
                className={`${TABLA.filaClic} ${BORDE_GRAVEDAD[alerta.severidad]}`}
                aria-label={`Alerta ${alerta.numero}, ${alerta.paciente.nombres} ${alerta.paciente.apellidos}`}
              >
                <td className={`${TABLA.td} cifra font-semibold text-navy`}>#{alerta.numero}</td>
                <td className={`${TABLA.td} whitespace-nowrap`}>
                  <div className="cifra">{formatearHoraReciente(alerta.recepcion)}</div>
                  <div className="text-xs text-apagado">{formatearAntiguedad(alerta.recepcion)}</div>
                </td>
                <td className={TABLA.td}>
                  <b className="text-navy">
                    {alerta.paciente.nombres} {alerta.paciente.apellidos}
                  </b>
                  {alerta.paciente.edad !== null && <span className="text-apagado"> ({alerta.paciente.edad} a.)</span>}
                </td>
                <td className={TABLA.td}>
                  <div className="flex flex-wrap gap-1">
                    <InsigniaGravedad severidad={alerta.severidad} />
                    {alerta.simulacro && <InsigniaSimulacro />}
                  </div>
                </td>
                <td className={TABLA.td}>{CONDICION[alerta.tipoEvento]}</td>
                {esSalud && (
                  <td className={TABLA.td}>
                    {alerta.prioridadMaxima ? (
                      <b className="text-magenta">{describirContacto(alerta)}</b>
                    ) : (
                      describirContacto(alerta)
                    )}
                  </td>
                )}
                <td className={TABLA.td}>
                  <InsigniaEstado estado={alerta.estadoAtencion} />
                </td>
                <td className={TABLA.td}>{alerta.responsable ?? <b className="text-magenta">Por asignar</b>}</td>
              </tr>
            ))}
          </Tabla>
          <Paginacion estado={paginacion} />
        </>
      )}
    </>
  );
}

/** Contador de abiertas de una pestaña. */
function Contador({ n }: { n: number }) {
  return (
    <span className="cifra rounded border border-linea bg-superficie-sub px-1.5 py-px text-[11px] text-apagado">
      {n} abiertas
    </span>
  );
}
