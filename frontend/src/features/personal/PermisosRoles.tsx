import { useState } from 'react';
import { ErrorApi } from '../../api/cliente';
import { Aviso, Cargando, Vacio } from '../../comun/ui/Aviso';
import { Boton } from '../../comun/ui/Boton';
import { Panel } from '../../comun/ui/Panel';
import { Segmentado } from '../../comun/ui/Pestanas';
import { TABLA } from '../../comun/ui/Tabla';
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
    <Panel titulo="Matriz de permisos por rol">
      {error && <Aviso tono="error">{error}</Aviso>}
      {cargando && !datos && <Cargando />}
      {!cargando && !error && roles.length === 0 && <Vacio>No hay roles registrados.</Vacio>}

      {rol && (
        <div className="space-y-4">
          <Segmentado
            etiqueta="Rol"
            opciones={roles.map((r) => ({ clave: r.codigo, texto: r.nombre || nombreRol(r.codigo) }))}
            activa={rol.codigo}
            alCambiar={elegirRol}
          />

          {rol.esSistema && (
            <Aviso tono="info">
              El administrador tiene todos los permisos y no se pueden cambiar, para que siempre haya alguien que
              administre las cuentas.
            </Aviso>
          )}

          <div className={TABLA.contenedor}>
            <table className={TABLA.tabla} aria-label={`Permisos de ${rol.nombre || nombreRol(rol.codigo)}`}>
              <thead>
                <tr>
                  <th scope="col" className={TABLA.th}>
                    Módulo
                  </th>
                  {ACCIONES.map((accion) => (
                    <th key={accion.valor} scope="col" className={`${TABLA.th} text-center`}>
                      {accion.etiqueta}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {MODULOS.map((modulo) => (
                  <tr key={modulo.valor} className={TABLA.fila}>
                    <th scope="row" className={`${TABLA.td} text-left font-bold text-navy`}>
                      {modulo.etiqueta}
                    </th>
                    {ACCIONES.map((accion) => {
                      const permiso: Permiso = `${modulo.valor}:${accion.valor}`;
                      return (
                        <td key={accion.valor} className={`${TABLA.td} text-center`}>
                          <input
                            type="checkbox"
                            checked={marcados.has(permiso)}
                            onChange={() => alternar(permiso)}
                            disabled={!editable}
                            aria-label={`${modulo.etiqueta}: ${accion.etiqueta}`}
                            className="size-4.5 accent-teal"
                          />
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {errorGuardar && <Aviso tono="error">{errorGuardar}</Aviso>}

          {editable && (
            <div className="flex flex-wrap items-center justify-end gap-3">
              {guardado && (
                <span role="status" className="text-[13px] text-verde-texto">
                  Permisos guardados. Se aplican cuando cada persona vuelve a iniciar sesión.
                </span>
              )}
              <Boton variante="principal" onClick={guardar} disabled={guardando || !editados}>
                {guardando ? 'Guardando…' : 'Guardar permisos'}
              </Boton>
            </div>
          )}
        </div>
      )}
    </Panel>
  );
}
