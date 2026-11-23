import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { configurarCliente, ErrorApi } from '../../api/cliente';
import { almacenToken } from './almacenToken';
import { sesionApi } from './sesionApi';
import type { Permiso, RespuestaLogin, Usuario } from './tipos';
import { marcarActividad, useControlInactividad } from './useControlInactividad';

/** Clave compartida entre pestañas: al cambiar, las demás también cierran su sesión. */
const CLAVE_CIERRE = 'alertacuida.cierreSesion';

/** Lo que cualquier componente puede saber y hacer con la sesión. */
interface Sesion {
  usuario: Usuario | null;
  /** true mientras se intenta recuperar la sesión al abrir o recargar la página */
  cargando: boolean;
  /** Por qué se cerró la sesión sin que la persona lo pidiera, para mostrarlo en el login */
  avisoCierre: string | null;
  iniciar(run: string, contrasena: string): Promise<void>;
  salir(): void;
  puede(permiso: Permiso): boolean;
}

const SesionContext = createContext<Sesion | null>(null);

/** Avisa a las demás pestañas que la sesión se cerró. */
function avisarCierre() {
  try {
    localStorage.setItem(CLAVE_CIERRE, String(Date.now()));
  } catch {
    // Sin almacenamiento: las otras pestañas se enteran en su siguiente renovación
  }
}

/** Guarda la sesión para toda la aplicación, la renueva y la recupera al recargar la página. */
export function SesionProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<Usuario | null>(null);
  const [cargando, setCargando] = useState(true);
  const [inactividadMs, setInactividadMs] = useState<number | null>(null);
  const [avisoCierre, setAvisoCierre] = useState<string | null>(null);
  const ultimaRenovacion = useRef(0);
  // Una sola renovación a la vez: varias peticiones pueden recibir 401 al mismo tiempo
  const renovacionEnCurso = useRef<Promise<boolean> | null>(null);
  // Sube en cada cierre: una renovación pedida antes no debe reabrir la sesión al responder
  const epocaSesion = useRef(0);

  /** Deja la sesión lista con lo que respondió el backend. */
  const aplicar = useCallback((respuesta: RespuestaLogin) => {
    almacenToken.guardar(respuesta.token);
    ultimaRenovacion.current = Date.now();
    setInactividadMs(respuesta.inactividadSegundos * 1000);
    setUsuario(respuesta.usuario);
  }, []);

  /** Olvida la sesión en esta pestaña, sin avisar a nadie más. */
  const limpiar = useCallback(() => {
    epocaSesion.current++;
    almacenToken.borrar();
    setInactividadMs(null);
    setUsuario(null);
  }, []);

  /** Cierra la sesión aquí, en las demás pestañas y en el servidor. */
  const salir = useCallback(() => {
    limpiar();
    avisarCierre();
    sesionApi.salir().catch(() => {
      // Sin conexión: el token de renovación vence solo por inactividad
    });
  }, [limpiar]);

  /** Pide tokens nuevos. Responde false si la sesión terminó; lanza el error si fue una falla de red. */
  const renovar = useCallback((): Promise<boolean> => {
    const epoca = epocaSesion.current;
    renovacionEnCurso.current ??= sesionApi
      .renovar()
      .then((respuesta) => {
        if (epoca !== epocaSesion.current) {
          // Se cerró la sesión mientras tanto: esta respuesta dejó una cookie válida que hay que borrar
          sesionApi.salir().catch(() => {
            // Sin conexión: el token de renovación vence solo por inactividad
          });
          return false;
        }
        aplicar(respuesta);
        return true;
      })
      .catch((error: unknown) => {
        if (error instanceof ErrorApi && error.estado === 401) {
          return false;
        }
        throw error;
      })
      .finally(() => {
        renovacionEnCurso.current = null;
      });
    return renovacionEnCurso.current;
  }, [aplicar]);

  /** Renovación periódica: si el servidor dice que la sesión terminó, se cierra aquí también. */
  const renovarPeriodico = useCallback(async () => {
    if (!(await renovar())) {
      limpiar();
    }
  }, [renovar, limpiar]);

  /** Cierre por el plazo de inactividad, con el aviso para la pantalla de login. */
  const cerrarPorInactividad = useCallback(() => {
    setAvisoCierre('Tu sesión se cerró por inactividad. Ingresa de nuevo para continuar.');
    salir();
  }, [salir]);

  useEffect(() => {
    configurarCliente(almacenToken.leer, renovar, salir);
  }, [renovar, salir]);

  // Al abrir o recargar: el token no sobrevive, pero la cookie de renovación sí (si la sesión sigue viva)
  useEffect(() => {
    renovar()
      .then((renovada) => {
        if (renovada) marcarActividad();
      })
      .catch(() => {
        // Falla de red: se muestra el login
      })
      .finally(() => setCargando(false));
  }, [renovar]);

  // Si otra pestaña cierra la sesión, esta también
  useEffect(() => {
    function alCambiar(evento: StorageEvent) {
      if (evento.key === CLAVE_CIERRE) limpiar();
    }
    window.addEventListener('storage', alCambiar);
    return () => window.removeEventListener('storage', alCambiar);
  }, [limpiar]);

  useControlInactividad({ inactividadMs, ultimaRenovacion, renovar: renovarPeriodico, alVencer: cerrarPorInactividad });

  const iniciar = useCallback(
    async (run: string, contrasena: string) => {
      const respuesta = await sesionApi.iniciar(run, contrasena);
      marcarActividad();
      setAvisoCierre(null);
      aplicar(respuesta);
    },
    [aplicar],
  );

  const valor = useMemo<Sesion>(
    () => ({
      usuario,
      cargando,
      avisoCierre,
      iniciar,
      salir,
      puede: (permiso) => usuario?.permisos.includes(permiso) ?? false,
    }),
    [usuario, cargando, avisoCierre, iniciar, salir],
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
