import { useMemo, useState } from 'react';
import { CLASE_CAMPO, CLASE_ETIQUETA } from '../../comun/estilos';
import { formatearFechaHora, formatearHoraReciente } from '../../comun/fechas';
import { Aviso, Cargando, Vacio } from '../../comun/ui/Aviso';
import { Panel } from '../../comun/ui/Panel';
import { Paginacion, usePaginacion } from '../../comun/ui/Tabla';
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
  TOKEN_REUTILIZADO: 'Posible robo de sesión',
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
  ZONA_SEGURA: { nombre: 'zona segura', categoria: 'Usuarios' },
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
  CALENDARIO: { nombre: 'jornada hábil', categoria: 'Administración' },
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

/** Quién lo hizo. Sin cuenta es un login fallido con un RUN que no existe, o un cambio del sistema. */
function autor(registro: RegistroAuditoria): string {
  if (registro.usuario) return registro.usuario.nombre;
  return registro.accion === 'LOGIN_FALLIDO' ? 'Un RUN no registrado' : 'Sistema';
}

/** Cómo se rotula el identificador: una consulta guarda el id del usuario del programa, y un cambio, el del objeto. */
function rotuloObjeto(registro: RegistroAuditoria): string {
  return registro.accion === 'LEER' && registro.idEntidad !== 'LISTA' ? 'Usuario del programa' : 'Objeto';
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
  const [incluirLecturas, setIncluirLecturas] = useState(false);
  const { datos, error, cargando } = useDatos(() => auditoriaApi.recientes(incluirLecturas), String(incluirLecturas));
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

  const paginacion = usePaginacion(visibles, 20);

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
    <Panel titulo="Registro de auditoría">
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <label className="block w-52">
          <span className={CLASE_ETIQUETA}>Tipo de acción</span>
          <select
            value={filtro}
            onChange={(e) => setFiltro(e.target.value as Categoria | '')}
            className={CLASE_CAMPO}
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
        <label className="flex items-center gap-2 py-2 text-[13px] text-apagado">
          <input
            type="checkbox"
            checked={incluirLecturas}
            onChange={(e) => setIncluirLecturas(e.target.checked)}
          />
          <span>Incluir consultas de datos</span>
        </label>
      </div>

      {error && <Aviso tono="error">{error}</Aviso>}
      {cargando && !datos && <Cargando />}

      {datos && (
        <>
          <p className="mb-1 text-[12.5px] text-apagado">
            <b className="cifra text-navy">{visibles.length}</b> {visibles.length === 1 ? 'acción' : 'acciones'}
          </p>
          <ul>
            {paginacion.visibles.map((registro) => {
              const abierto = abiertos.has(registro.id);
              return (
                <li key={registro.id} className="flex items-start gap-3.5 border-t border-linea px-1 py-3">
                  <span
                    aria-hidden="true"
                    className="grid size-8.5 flex-none place-items-center rounded-full bg-teal-tinte font-bold text-teal-oscuro"
                  >
                    {ICONO[categoria(registro)]}
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="text-[13px] font-bold text-navy">{titulo(registro)}</div>
                    <div className="mt-0.5 text-[12.5px] text-apagado">
                      <span className="font-medium text-teal-oscuro">{autor(registro)}</span> ·{' '}
                      <time dateTime={registro.fechaHora} title={formatearFechaHora(registro.fechaHora)} className="cifra">
                        {formatearHoraReciente(registro.fechaHora)}
                      </time>
                    </div>
                    {abierto && (
                      <dl className="mt-2 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 rounded-panel bg-superficie-sub px-3 py-2 text-[12.5px]">
                        {registro.idEntidad && (
                          <>
                            <dt className="text-apagado">{rotuloObjeto(registro)}</dt>
                            <dd className="cifra break-all">{registro.idEntidad}</dd>
                          </>
                        )}
                        {Object.entries(registro.detalle ?? {}).map(([clave, valor]) => (
                          <div key={clave} className="contents">
                            <dt className="text-apagado">{clave}</dt>
                            <dd className="break-all">{comoTexto(valor)}</dd>
                          </div>
                        ))}
                        {registro.ip && (
                          <>
                            <dt className="text-apagado">IP</dt>
                            <dd className="cifra">{registro.ip}</dd>
                          </>
                        )}
                      </dl>
                    )}
                  </div>
                  <button
                    onClick={() => alternar(registro.id)}
                    aria-expanded={abierto}
                    aria-label={abierto ? 'Ocultar detalle' : 'Ver detalle'}
                    className={`rounded px-2 py-1 text-apagado hover:bg-superficie-sub hover:text-navy ${abierto ? 'rotate-180' : ''}`}
                  >
                    ⌄
                  </button>
                </li>
              );
            })}
          </ul>
          {visibles.length === 0 && (
            <Vacio>
              {datos.length === 0 ? 'Todavía no hay acciones registradas.' : 'No hay acciones con esos filtros.'}
            </Vacio>
          )}
          <Paginacion estado={paginacion} opciones={[20, 50, 100]} />
        </>
      )}
    </Panel>
  );
}
