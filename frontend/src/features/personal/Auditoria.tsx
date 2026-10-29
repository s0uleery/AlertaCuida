import { useMemo, useState } from 'react';
import { CLASE_CAMPO, CLASE_ERROR, CLASE_ETIQUETA } from '../../comun/estilos';
import { formatearFechaHora, formatearHoraReciente } from '../../comun/fechas';
import { useDatos } from '../../comun/useDatos';
import { auditoriaApi } from './auditoriaApi';
import type { AccionAuditoria, EntidadAuditoria, RegistroAuditoria } from './tipos';

/** Cómo se nombra cada acción en el título del registro. */
const ACCION: Record<AccionAuditoria, string> = {
  LEER: 'Consulta',
  CREAR: 'Creación',
  MODIFICAR: 'Modificación',
  ELIMINAR: 'Eliminación',
  EXPORTAR: 'Exportación',
  LOGIN: 'Inicio de sesión',
  LOGIN_FALLIDO: 'Inicio de sesión fallido',
};

/** Tipos de acción del filtro, como en el mockup, con su ícono. */
type Categoria = 'Alertas' | 'Usuarios' | 'Reportes' | 'Administración' | 'Sesión';
const ICONO: Record<Categoria, string> = { Alertas: '!', Usuarios: '✎', Reportes: '↓', Administración: '⚙', Sesión: '→' };

/** Nombre de cada entidad y el tipo de acción al que pertenece. */
const ENTIDAD: Record<EntidadAuditoria, { nombre: string; categoria: Categoria }> = {
  PACIENTE: { nombre: 'usuario del programa', categoria: 'Usuarios' },
  FICHA_PACIENTE: { nombre: 'ficha de un usuario', categoria: 'Usuarios' },
  CONDICION_PACIENTE: { nombre: 'condición de un usuario', categoria: 'Usuarios' },
  CONTACTO: { nombre: 'contacto', categoria: 'Usuarios' },
  CONTACTO_PACIENTE: { nombre: 'contacto de un usuario', categoria: 'Usuarios' },
  CONFIGURACION: { nombre: 'perfil clínico', categoria: 'Usuarios' },
  UMBRAL: { nombre: 'umbral', categoria: 'Usuarios' },
  DOCUMENTO: { nombre: 'documento', categoria: 'Usuarios' },
  SMARTWATCH: { nombre: 'reloj', categoria: 'Usuarios' },
  ASIGNACION_RELOJ: { nombre: 'asignación de reloj', categoria: 'Usuarios' },
  ALERTA: { nombre: 'alerta', categoria: 'Alertas' },
  ATENCION_ALERTA: { nombre: 'atención de alerta', categoria: 'Alertas' },
  SEGUIMIENTO: { nombre: 'seguimiento', categoria: 'Alertas' },
  USUARIO: { nombre: 'cuenta del equipo', categoria: 'Administración' },
  ROL: { nombre: 'rol', categoria: 'Administración' },
  PERMISO_ROL: { nombre: 'permisos de un rol', categoria: 'Administración' },
  REPORTE: { nombre: 'reporte', categoria: 'Reportes' },
};

/** Tipo de acción de un registro: el inicio de sesión no tiene entidad. */
function categoria(registro: RegistroAuditoria): Categoria {
  return registro.entidad ? ENTIDAD[registro.entidad].categoria : 'Sesión';
}

/** Título del registro, por ejemplo "Modificación de alerta". */
function titulo(registro: RegistroAuditoria): string {
  const accion = ACCION[registro.accion];
  return registro.entidad ? `${accion} de ${ENTIDAD[registro.entidad].nombre}` : accion;
}

/** Quién lo hizo; en un login fallido puede no haber cuenta. */
function autor(registro: RegistroAuditoria): string {
  return registro.usuario?.nombre ?? 'Un RUN no registrado';
}

/** Un valor del detalle como texto: los objetos y listas van en JSON. */
function comoTexto(valor: unknown): string {
  return typeof valor === 'object' && valor !== null ? JSON.stringify(valor) : String(valor);
}

/** true si el texto buscado está en quién lo hizo, el título o el identificador del objeto. */
function coincide(registro: RegistroAuditoria, busqueda: string): boolean {
  const texto = [autor(registro), titulo(registro), registro.idEntidad ?? ''].join(' ').toLowerCase();
  return texto.includes(busqueda.toLowerCase());
}

/**
 * Pestaña "Auditoría": quién leyó o cambió qué y cuándo, desde registro_auditoria. El registro no
 * guarda datos personales, así que del objeto solo se muestra su identificador.
 */
export function Auditoria() {
  const { datos, error, cargando } = useDatos(auditoriaApi.recientes);
  const [filtro, setFiltro] = useState<Categoria | ''>('');
  const [busqueda, setBusqueda] = useState('');
  const [abiertos, setAbiertos] = useState<Set<string>>(new Set());

  const visibles = useMemo(
    () =>
      (datos ?? []).filter(
        (registro) =>
          (!filtro || categoria(registro) === filtro) && (!busqueda.trim() || coincide(registro, busqueda.trim())),
      ),
    [datos, filtro, busqueda],
  );

  /** Abre o cierra el detalle de un registro. */
  function alternar(id: string) {
    setAbiertos((anteriores) => {
      const nuevos = new Set(anteriores);
      if (nuevos.has(id)) nuevos.delete(id);
      else nuevos.add(id);
      return nuevos;
    });
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-3">
        <label className="block w-52">
          <span className={CLASE_ETIQUETA}>Tipo de acción</span>
          <select
            value={filtro}
            onChange={(e) => setFiltro(e.target.value as Categoria | '')}
            className="mt-1 w-full rounded-sm border border-slate-300 bg-white px-3 py-2"
          >
            <option value="">Todas</option>
            {(Object.keys(ICONO) as Categoria[]).map((c) => (
              <option key={c}>{c}</option>
            ))}
          </select>
        </label>
        <label className="block min-w-52 flex-1">
          <span className={CLASE_ETIQUETA}>Buscar por perfil u objeto</span>
          <input
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            placeholder="Ej: nombre de la persona o tipo de objeto"
            className={CLASE_CAMPO}
          />
        </label>
      </div>

      {error && <p className={CLASE_ERROR}>{error}</p>}
      {cargando && <p className="text-slate-500">Cargando…</p>}

      {datos && (
        <>
          <h2 className="text-lg font-medium text-tinta">
            {visibles.length} {visibles.length === 1 ? 'acción' : 'acciones'}
          </h2>
          <ul>
            {visibles.map((registro) => {
              const abierto = abiertos.has(registro.id);
              return (
                <li key={registro.id} className="flex items-start gap-3.5 border-t border-slate-300 px-1 py-3.5">
                  <span
                    aria-hidden="true"
                    className="grid size-8.5 flex-none place-items-center rounded-full bg-slate-100 font-bold text-prlac"
                  >
                    {ICONO[categoria(registro)]}
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="font-bold text-tinta">{titulo(registro)}</div>
                    <div className="mt-0.5">
                      <span className="font-medium text-prlac">{autor(registro)}</span> •{' '}
                      <time dateTime={registro.fechaHora} title={formatearFechaHora(registro.fechaHora)}>
                        {formatearHoraReciente(registro.fechaHora)}
                      </time>
                    </div>
                    {abierto && (
                      <dl className="mt-2 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 rounded-sm bg-slate-100 px-3 py-2 text-[13px]">
                        {registro.idEntidad && (
                          <>
                            <dt className="text-slate-500">Objeto</dt>
                            <dd className="font-mono break-all">{registro.idEntidad}</dd>
                          </>
                        )}
                        {Object.entries(registro.detalle ?? {}).map(([clave, valor]) => (
                          <div key={clave} className="contents">
                            <dt className="text-slate-500">{clave}</dt>
                            <dd className="break-all">{comoTexto(valor)}</dd>
                          </div>
                        ))}
                        {registro.ip && (
                          <>
                            <dt className="text-slate-500">IP</dt>
                            <dd className="font-mono">{registro.ip}</dd>
                          </>
                        )}
                      </dl>
                    )}
                  </div>
                  <button
                    onClick={() => alternar(registro.id)}
                    aria-expanded={abierto}
                    aria-label={abierto ? 'Ocultar detalle' : 'Ver detalle'}
                    className={`px-2 py-1 text-slate-500 hover:text-tinta ${abierto ? 'rotate-180' : ''}`}
                  >
                    ⌄
                  </button>
                </li>
              );
            })}
            {visibles.length === 0 && (
              <li className="border-t border-slate-300 px-1 py-3.5 text-slate-500">
                {datos.length === 0 ? 'Todavía no hay acciones registradas.' : 'No hay acciones con esos filtros.'}
              </li>
            )}
          </ul>
        </>
      )}
    </div>
  );
}
