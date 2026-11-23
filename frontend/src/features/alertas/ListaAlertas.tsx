import { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { CLASE_CAMPO, CLASE_ERROR, CLASE_ETIQUETA, CLASE_TARJETA } from '../../comun/estilos';
import { formatearAntiguedad, formatearHoraReciente } from '../../comun/fechas';
import { useDatos } from '../../comun/useDatos';
import { alertasApi } from './alertasApi';
import { CONDICION, describirContacto, ESTADO, InsigniaGravedad, InsigniaSimulacro } from './etiquetas';
import type { CategoriaAlerta, ResumenAlerta, Severidad } from './tipos';

/** Qué alertas se muestran según su atención. */
type FiltroEstado = 'ABIERTAS' | 'CERRADAS' | 'TODAS';

/** Pestañas: las de salud y las del reloj se atienden distinto. */
const PESTANAS: { categoria: CategoriaAlerta; titulo: string }[] = [
  { categoria: 'SALUD', titulo: 'Salud' },
  { categoria: 'TECNICA', titulo: 'Reloj' },
];

/** Borde de la fila: las medias y las graves se destacan; las leves no. */
const BORDE_GRAVEDAD: Record<Severidad, string> = {
  GRAVE: 'border-l-4 border-l-[#c81e1e] bg-[#fff6f6]',
  MEDIA: 'border-l-4 border-l-[#d99a00] bg-[#fffaf0]',
  LEVE: 'border-l-4 border-l-transparent',
};

/** true si el equipo todavía no la cierra (en seguimiento cuenta como abierta). */
function estaAbierta(alerta: ResumenAlerta): boolean {
  return alerta.estadoAtencion !== 'RESUELTA';
}

/** true si el texto buscado está en el nombre del usuario o en el folio (#142 o 142). */
function coincide(alerta: ResumenAlerta, busqueda: string): boolean {
  const texto = `${alerta.paciente.nombres} ${alerta.paciente.apellidos} #${alerta.numero}`.toLowerCase();
  return texto.includes(busqueda.toLowerCase());
}

/**
 * Registro de alertas, en pestañas Salud y Reloj. Por defecto muestra las abiertas, en el orden del backend:
 * primero las que nadie respondió y después las más recientes. Los simulacros aparecen con su etiqueta y las
 * canceladas por el usuario no aparecen (solo sirven para métricas).
 */
export function ListaAlertas() {
  const { datos, error, cargando } = useDatos(alertasApi.listar);
  const [pestana, setPestana] = useState<CategoriaAlerta>('SALUD');
  const [filtro, setFiltro] = useState<FiltroEstado>('ABIERTAS');
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
          (!busqueda.trim() || coincide(a, busqueda.trim())),
      ),
    [datos, pestana, filtro, busqueda],
  );
  const esSalud = pestana === 'SALUD';

  return (
    <section className={`${CLASE_TARJETA} space-y-4`}>
      <div>
        <h1 className="text-2xl font-medium text-tinta">Alertas</h1>
        <p className="text-tinta">
          Registro de las alertas recibidas. Las medias y graves van destacadas, y las que nadie respondió van primero.
        </p>
      </div>

      <div role="tablist" aria-label="Tipo de alerta" className="flex gap-2 border-b border-slate-200">
        {PESTANAS.map(({ categoria, titulo }) => {
          const activa = categoria === pestana;
          return (
            <button
              key={categoria}
              type="button"
              role="tab"
              aria-selected={activa}
              onClick={() => setPestana(categoria)}
              className={`-mb-px border-b-2 px-4 py-2 font-medium ${
                activa ? 'border-prlac text-prlac' : 'border-transparent text-slate-500 hover:text-slate-700'
              }`}
            >
              {titulo}
              <span className="ml-2 rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
                {abiertasPorPestana(categoria)} abiertas
              </span>
            </button>
          );
        })}
      </div>

      <div className="flex flex-wrap items-end gap-3">
        <label className="block min-w-52 flex-1">
          <span className={CLASE_ETIQUETA}>Buscar</span>
          <input
            id="busqueda-alertas"
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            placeholder="Nombre del usuario o folio"
            className={CLASE_CAMPO}
          />
        </label>
        <label className="block w-52">
          <span className={CLASE_ETIQUETA}>Mostrar</span>
          <select
            id="filtro-alertas"
            value={filtro}
            onChange={(e) => setFiltro(e.target.value as FiltroEstado)}
            className={CLASE_CAMPO}
          >
            <option value="ABIERTAS">Abiertas</option>
            <option value="CERRADAS">Cerradas</option>
            <option value="TODAS">Todas</option>
          </select>
        </label>
      </div>

      {error && <p className={CLASE_ERROR}>{error}</p>}

      <div className="overflow-x-auto rounded-lg border border-prlac">
        <table className="w-full min-w-[860px] border-collapse text-left text-sm">
          <thead className="bg-prlac text-white">
            <tr>
              <th className="px-3 py-2.5 font-bold">Folio</th>
              <th className="px-3 py-2.5 font-bold">Recibida</th>
              <th className="px-3 py-2.5 font-bold">Usuario</th>
              <th className="px-3 py-2.5 font-bold">Gravedad</th>
              <th className="px-3 py-2.5 font-bold">{esSalud ? 'Condición' : 'Problema del reloj'}</th>
              {esSalud && <th className="px-3 py-2.5 font-bold">Contactos</th>}
              <th className="px-3 py-2.5 font-bold">Atención</th>
              <th className="px-3 py-2.5 font-bold">Responsable</th>
            </tr>
          </thead>
          <tbody>
            {visibles.map((alerta) => (
              <tr key={alerta.id} className={`border-t border-slate-300 ${BORDE_GRAVEDAD[alerta.severidad]}`}>
                <td className="px-3 py-2.5 font-medium tabular-nums">
                  <Link to={`/alertas/${alerta.id}`} className="text-sky-700 hover:underline" title="Ver la ficha">
                    #{alerta.numero}
                  </Link>
                </td>
                <td className="px-3 py-2.5">
                  <div>{formatearHoraReciente(alerta.recepcion)}</div>
                  <div className="text-xs text-slate-500">{formatearAntiguedad(alerta.recepcion)}</div>
                </td>
                <td className="px-3 py-2.5">
                  {alerta.paciente.nombres} {alerta.paciente.apellidos}
                  {alerta.paciente.edad !== null && `, ${alerta.paciente.edad}`}
                </td>
                <td className="px-3 py-2.5">
                  <div className="flex flex-wrap gap-1">
                    <InsigniaGravedad severidad={alerta.severidad} />
                    {alerta.simulacro && <InsigniaSimulacro />}
                  </div>
                </td>
                <td className="px-3 py-2.5">{CONDICION[alerta.tipoEvento]}</td>
                {esSalud && (
                  <td className="px-3 py-2.5">
                    {alerta.prioridadMaxima ? (
                      <span className="font-bold text-[#a1111a]">{describirContacto(alerta)}</span>
                    ) : (
                      describirContacto(alerta)
                    )}
                  </td>
                )}
                <td className="px-3 py-2.5">{ESTADO[alerta.estadoAtencion]}</td>
                <td className="px-3 py-2.5">
                  {alerta.responsable ?? <span className="text-slate-400">Sin asignar</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {cargando && <p className="px-3 py-2.5 text-slate-500">Cargando…</p>}
        {!cargando && !error && visibles.length === 0 && (
          <p className="px-3 py-2.5 text-slate-500">
            {filtro === 'ABIERTAS' ? 'No hay alertas abiertas en esta pestaña.' : 'No hay alertas que mostrar.'}
          </p>
        )}
      </div>
    </section>
  );
}
