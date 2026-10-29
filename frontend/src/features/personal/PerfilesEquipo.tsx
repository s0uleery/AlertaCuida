import { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { ErrorApi } from '../../api/cliente';
import { useConfirmar } from '../../comun/Confirmacion';
import { CLASE_CAMPO, CLASE_ERROR } from '../../comun/estilos';
import { formatearHoraReciente } from '../../comun/fechas';
import { useDatos } from '../../comun/useDatos';
import { useSesion } from '../sesion/SesionContext';
import { personalApi } from './personalApi';
import { nombreRol, ROLES } from './roles';
import type { EstadoCuenta, PerfilEquipo, Rol } from './tipos';

/** Cómo se muestra cada estado de la cuenta. */
const ESTADO: Record<EstadoCuenta, { texto: string; clase: string }> = {
  ACTIVA: { texto: 'Activo', clase: 'bg-[#dff2de] text-[#14540f]' },
  PENDIENTE: { texto: 'Pendiente de activar', clase: 'bg-[#fff0cc] text-[#7a4e00]' },
  DESACTIVADA: { texto: 'Desactivado', clase: 'bg-slate-200 text-slate-600' },
};

/** true si el texto buscado está en el nombre, cargo, correo o rol (sin importar mayúsculas ni tildes). */
function coincide(perfil: PerfilEquipo, busqueda: string): boolean {
  const normalizar = (texto: string) => texto.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
  const texto = [perfil.nombre, perfil.cargo ?? '', perfil.correo, nombreRol(perfil.rol)].join(' ');
  return normalizar(texto).includes(normalizar(busqueda));
}

/**
 * Pestaña "Perfiles": las cuentas del equipo tal como están en la base. Quien tiene
 * ADMINISTRACION:EDITAR puede cambiar el rol de los demás desde la tabla.
 */
export function PerfilesEquipo() {
  const { usuario, puede } = useSesion();
  const confirmar = useConfirmar();
  const [busqueda, setBusqueda] = useState('');
  const [guardando, setGuardando] = useState<string | null>(null);
  const [errorCambio, setErrorCambio] = useState<string | null>(null);
  const { datos, error, cargando, recargar } = useDatos(personalApi.listar);

  const visibles = useMemo(
    () => (datos ?? []).filter((perfil) => !busqueda.trim() || coincide(perfil, busqueda.trim())),
    [datos, busqueda],
  );

  /** Pide confirmación y cambia el rol en el backend. */
  async function cambiarRol(perfil: PerfilEquipo, rol: Rol) {
    const confirmado = await confirmar({
      titulo: `¿Cambiar el rol de ${perfil.nombre}?`,
      mensaje: `Pasará de ${nombreRol(perfil.rol)} a ${nombreRol(rol)}. El cambio se aplica la próxima vez que inicie sesión.`,
      textoConfirmar: 'Cambiar rol',
    });
    if (!confirmado) return;
    setGuardando(perfil.id);
    setErrorCambio(null);
    try {
      await personalApi.cambiarRol(perfil.id, rol);
      recargar();
    } catch (e) {
      setErrorCambio(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.');
    } finally {
      setGuardando(null);
    }
  }

  const editable = puede('ADMINISTRACION:EDITAR');

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-lg font-medium text-tinta">Perfiles del equipo</h2>
        {puede('ADMINISTRACION:CREAR') && (
          <Link
            to="/administracion/nuevo"
            className="rounded-sm bg-prlac px-4 py-2 font-medium text-white hover:bg-prlac-oscuro"
          >
            Agregar perfil
          </Link>
        )}
      </div>

      <input
        value={busqueda}
        onChange={(e) => setBusqueda(e.target.value)}
        placeholder="Buscar por nombre, cargo, correo o rol"
        aria-label="Buscar"
        className={`${CLASE_CAMPO} mt-0 max-w-sm`}
      />

      {error && <p className={CLASE_ERROR}>{error}</p>}
      {errorCambio && (
        <p role="alert" className={CLASE_ERROR}>
          {errorCambio}
        </p>
      )}

      <div className="overflow-x-auto rounded-lg border border-slate-200">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Nombre</th>
              <th className="px-4 py-3 font-medium">Cargo</th>
              <th className="px-4 py-3 font-medium">Rol</th>
              <th className="px-4 py-3 font-medium">Estado</th>
              <th className="px-4 py-3 font-medium">Último acceso</th>
            </tr>
          </thead>
          <tbody>
            {visibles.map((perfil) => {
              const propio = perfil.id === usuario?.id;
              return (
                <tr key={perfil.id} className="border-b border-slate-100 last:border-0">
                  <td className="px-4 py-3">
                    <span className="font-medium text-slate-900">{perfil.nombre}</span>
                    {propio && <span className="ml-1 text-slate-500">(tú)</span>}
                    <span className="block text-xs text-slate-500">{perfil.correo}</span>
                  </td>
                  <td className="px-4 py-3">{perfil.cargo || <span className="text-slate-400">—</span>}</td>
                  <td className="px-4 py-3">
                    {editable ? (
                      <select
                        value={perfil.rol}
                        onChange={(e) => cambiarRol(perfil, e.target.value as Rol)}
                        disabled={propio || guardando === perfil.id}
                        title={propio ? 'No puedes cambiar tu propio rol' : undefined}
                        aria-label={`Rol de ${perfil.nombre}`}
                        className="w-full max-w-48 rounded-md border border-slate-300 bg-white px-2 py-1.5 focus:border-sky-600 focus:outline-none disabled:bg-slate-100"
                      >
                        {ROLES.map((rol) => (
                          <option key={rol.valor} value={rol.valor}>
                            {rol.etiqueta}
                          </option>
                        ))}
                      </select>
                    ) : (
                      nombreRol(perfil.rol)
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <span
                      className={`inline-block rounded-full px-2 py-0.5 text-xs ${ESTADO[perfil.estado].clase}`}
                    >
                      {ESTADO[perfil.estado].texto}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    {perfil.ultimoAcceso ? (
                      formatearHoraReciente(perfil.ultimoAcceso)
                    ) : (
                      <span className="text-slate-400">Nunca</span>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        {cargando && <p className="px-4 py-3 text-slate-500">Cargando…</p>}
        {!cargando && !error && visibles.length === 0 && (
          <p className="px-4 py-3 text-slate-500">
            {busqueda ? 'Nadie coincide con la búsqueda.' : 'Todavía no hay cuentas del equipo.'}
          </p>
        )}
      </div>
    </div>
  );
}
