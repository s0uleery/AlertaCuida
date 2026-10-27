import { useState } from 'react';
import { ErrorApi } from '../../api/cliente';
import { CLASE_ERROR, CLASE_ETIQUETA } from '../../comun/estilos';
import { useDatos } from '../../comun/useDatos';
import { useSesion } from '../sesion/SesionContext';
import type { Accion, Modulo, Permiso } from '../sesion/tipos';
import { personalApi } from './personalApi';
import { nombreRol } from './roles';

/** Filas de la matriz: los módulos de permiso_rol. */
const MODULOS: { valor: Modulo; etiqueta: string }[] = [
  { valor: 'ALERTAS', etiqueta: 'Alertas' },
  { valor: 'USUARIOS', etiqueta: 'Usuarios' },
  { valor: 'REPORTES', etiqueta: 'Reportes' },
  { valor: 'ADMINISTRACION', etiqueta: 'Administración' },
];

/** Columnas de la matriz: las acciones de permiso_rol. */
const ACCIONES: { valor: Accion; etiqueta: string }[] = [
  { valor: 'VER', etiqueta: 'Ver' },
  { valor: 'CREAR', etiqueta: 'Crear' },
  { valor: 'EDITAR', etiqueta: 'Editar' },
  { valor: 'ELIMINAR', etiqueta: 'Eliminar' },
  { valor: 'EXPORTAR', etiqueta: 'Exportar' },
];

/**
 * Pestaña "Permisos": la matriz de módulos y acciones del rol elegido, leída de permiso_rol.
 * Los del administrador no se editan, para no dejar a nadie sin acceso a esta sección.
 */
export function PermisosRoles() {
  const { puede } = useSesion();
  const { datos, error, cargando, recargar } = useDatos(personalApi.roles);
  const [seleccion, setSeleccion] = useState<string | null>(null);
  // null = sin cambios: se muestran los permisos guardados
  const [editados, setEditados] = useState<Set<Permiso> | null>(null);
  const [guardando, setGuardando] = useState(false);
  const [errorGuardar, setErrorGuardar] = useState<string | null>(null);
  const [guardado, setGuardado] = useState(false);

  const roles = datos ?? [];
  const rol = roles.find((r) => r.codigo === seleccion) ?? roles[0] ?? null;
  const marcados = editados ?? new Set(rol?.permisos ?? []);
  const editable = !!rol && !rol.esSistema && puede('ADMINISTRACION:EDITAR');

  /** Cambia de rol y descarta lo que no se guardó. */
  function elegirRol(codigo: string) {
    setSeleccion(codigo);
    setEditados(null);
    setErrorGuardar(null);
    setGuardado(false);
  }

  /** Marca o desmarca un permiso. */
  function alternar(permiso: Permiso) {
    const nuevos = new Set(marcados);
    if (nuevos.has(permiso)) nuevos.delete(permiso);
    else nuevos.add(permiso);
    setEditados(nuevos);
    setGuardado(false);
  }

  /** Reemplaza los permisos del rol en el backend. */
  async function guardar() {
    if (!rol) return;
    setGuardando(true);
    setErrorGuardar(null);
    try {
      await personalApi.guardarPermisos(rol.codigo, [...marcados]);
      setEditados(null);
      setGuardado(true);
      recargar();
    } catch (e) {
      setErrorGuardar(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.');
    } finally {
      setGuardando(false);
    }
  }

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-medium text-tinta">Permisos por rol</h2>

      {error && <p className={CLASE_ERROR}>{error}</p>}
      {cargando && !datos && <p className="text-slate-500">Cargando…</p>}
      {!cargando && !error && roles.length === 0 && <p className="text-slate-500">No hay roles registrados.</p>}

      {rol && (
        <>
          <label className="block w-60">
            <span className={CLASE_ETIQUETA}>Rol</span>
            <select
              value={rol.codigo}
              onChange={(e) => elegirRol(e.target.value)}
              className="mt-1 w-full rounded-sm border border-slate-300 bg-white px-3 py-2"
            >
              {roles.map((r) => (
                <option key={r.codigo} value={r.codigo}>
                  {r.nombre || nombreRol(r.codigo)}
                </option>
              ))}
            </select>
          </label>

          {rol.esSistema && (
            <p className="text-sm text-slate-500">
              El administrador tiene todos los permisos y no se pueden cambiar, para que siempre haya alguien que
              administre las cuentas.
            </p>
          )}

          <div className="overflow-x-auto rounded-lg border border-slate-200">
            <table className="w-full text-sm">
              <thead className="border-b border-slate-200 text-slate-500">
                <tr>
                  <th className="px-4 py-3 text-left font-medium">Módulo</th>
                  {ACCIONES.map((accion) => (
                    <th key={accion.valor} className="px-4 py-3 text-center font-medium">
                      {accion.etiqueta}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {MODULOS.map((modulo) => (
                  <tr key={modulo.valor} className="border-b border-slate-100 last:border-0">
                    <td className="px-4 py-3">{modulo.etiqueta}</td>
                    {ACCIONES.map((accion) => {
                      const permiso: Permiso = `${modulo.valor}:${accion.valor}`;
                      return (
                        <td key={accion.valor} className="px-4 py-3 text-center">
                          <input
                            type="checkbox"
                            checked={marcados.has(permiso)}
                            onChange={() => alternar(permiso)}
                            disabled={!editable}
                            aria-label={`${modulo.etiqueta}: ${accion.etiqueta}`}
                            className="size-4.5"
                          />
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {errorGuardar && (
            <p role="alert" className={CLASE_ERROR}>
              {errorGuardar}
            </p>
          )}

          {editable && (
            <div className="flex flex-wrap items-center justify-end gap-3">
              {guardado && (
                <span className="text-sm text-emerald-700">
                  Permisos guardados. Se aplican cuando cada persona vuelve a iniciar sesión.
                </span>
              )}
              <button
                onClick={guardar}
                disabled={guardando || !editados}
                className="rounded-sm bg-prlac px-4 py-2 font-medium text-white hover:bg-prlac-oscuro disabled:opacity-50"
              >
                {guardando ? 'Guardando…' : 'Guardar permisos'}
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
