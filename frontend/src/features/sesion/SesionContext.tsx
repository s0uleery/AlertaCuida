import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { configurarCliente } from '../../api/cliente';
import { almacenToken } from './almacenToken';
import { sesionApi } from './sesionApi';
import type { Permiso, Usuario } from './tipos';

/** Lo que cualquier componente puede saber y hacer con la sesión. */
interface Sesion {
  usuario: Usuario | null;
  /** true mientras se recupera la sesión guardada al recargar la página */
  cargando: boolean;
  iniciar(run: string, contrasena: string): Promise<void>;
  salir(): void;
  puede(permiso: Permiso): boolean;
}

const SesionContext = createContext<Sesion | null>(null);

/** Guarda la sesión para toda la aplicación y la recupera al recargar la página. */
export function SesionProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<Usuario | null>(null);
  const [cargando, setCargando] = useState(() => almacenToken.leer() !== null);

  const salir = useCallback(() => {
    almacenToken.borrar();
    setUsuario(null);
  }, []);

  useEffect(() => {
    configurarCliente(almacenToken.leer, salir);
  }, [salir]);

  // Al recargar: si hay token guardado, se pregunta al backend quién es (y si sigue vigente)
  useEffect(() => {
    if (!almacenToken.leer()) return;
    sesionApi
      .usuarioActual()
      .then(setUsuario)
      .catch(salir)
      .finally(() => setCargando(false));
  }, [salir]);

  const iniciar = useCallback(async (run: string, contrasena: string) => {
    const respuesta = await sesionApi.iniciar(run, contrasena);
    almacenToken.guardar(respuesta.token);
    setUsuario(respuesta.usuario);
  }, []);

  const valor = useMemo<Sesion>(
    () => ({
      usuario,
      cargando,
      iniciar,
      salir,
      puede: (permiso) => usuario?.permisos.includes(permiso) ?? false,
    }),
    [usuario, cargando, iniciar, salir],
  );

  return <SesionContext.Provider value={valor}>{children}</SesionContext.Provider>;
}

/** @returns la sesión actual. Solo funciona dentro de <SesionProvider>. */
export function useSesion(): Sesion {
  const sesion = useContext(SesionContext);
  if (!sesion) {
    throw new Error('useSesion debe usarse dentro de <SesionProvider>');
  }
  return sesion;
}
