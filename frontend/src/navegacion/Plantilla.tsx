import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router';
import { panelApi } from '../features/inicio/panelApi';
import { useSesion } from '../features/sesion/SesionContext';
import { FranjaGobierno, TriadaMarcas } from './Marcas';
import { SECCIONES } from './menu';

/** Cómo se muestra cada rol bajo el nombre. */
export const NOMBRE_ROL: Record<string, string> = {
  ADMINISTRADOR: 'Administrador',
  COORDINADOR: 'Coordinador PRLAC',
  PERSONAL: 'Personal',
};

/** Cada cuánto se actualiza el contador de alertas abiertas (cada lectura del panel queda en la auditoría). */
const REFRESCO_CONTADOR_MS = 5 * 60_000;

/** @returns las iniciales del nombre (dos letras), para el avatar */
function iniciales(nombre: string): string {
  const partes = nombre.trim().split(/\s+/);
  return ((partes[0]?.[0] ?? '') + (partes.length > 1 ? partes[partes.length - 1][0] : '')).toUpperCase();
}

/**
 * Encabezado institucional de dos niveles del mockup: franja del gobierno, tríada de marcas (lleva a Inicio) y el
 * usuario con su menú; debajo, las secciones según los permisos del rol, con el contador de alertas abiertas.
 * Cada página se dibuja en &lt;Outlet /&gt;.
 */
export function Plantilla() {
  const { usuario, puede } = useSesion();
  const visibles = SECCIONES.filter((seccion) => !seccion.permiso || puede(seccion.permiso));
  const abiertas = useAlertasAbiertas(puede('ALERTAS:VER'));

  return (
    <div className="min-h-screen bg-fondo">
      <header className="sticky top-0 z-30 border-b border-linea-oscura bg-superficie">
        <FranjaGobierno />
        <div className="mx-auto flex max-w-[1380px] flex-wrap items-center justify-between gap-5 px-7 py-3 max-[760px]:px-4 max-[760px]:py-2.5">
          <Link to="/" className="-mx-1.5 -my-1 rounded-md px-1.5 py-1 hover:bg-[#f8fbfc]" title="AlertaCuida · Inicio">
            <TriadaMarcas />
          </Link>
          {usuario && <MenuUsuario nombre={usuario.nombre} rol={usuario.rol} />}
        </div>
        <div className="border-t border-linea bg-[#fbfcfd]">
          <nav
            aria-label="Navegación principal"
            className="mx-auto flex max-w-[1380px] gap-1 overflow-x-auto px-7 max-[760px]:px-4"
          >
            <EnlaceSeccion ruta="/" fin>
              Inicio
            </EnlaceSeccion>
            {visibles.map((seccion) => (
              <EnlaceSeccion key={seccion.ruta} ruta={seccion.ruta}>
                {seccion.etiqueta}
                {seccion.ruta === '/alertas' && abiertas !== null && abiertas > 0 && (
                  <span
                    className="cifra rounded border border-magenta-borde bg-magenta-tinte px-1.5 py-px text-[11px] font-bold text-magenta"
                    aria-label={`${abiertas} abiertas`}
                  >
                    {abiertas}
                  </span>
                )}
              </EnlaceSeccion>
            ))}
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-[1380px] px-7 pt-6 pb-14 max-[760px]:px-4">
        <Outlet />
      </main>
    </div>
  );
}

/** Una sección de la barra: la activa va en navy, en negrita y subrayada en turquesa. */
function EnlaceSeccion({ ruta, fin = false, children }: { ruta: string; fin?: boolean; children: ReactNode }) {
  return (
    <NavLink
      to={ruta}
      end={fin}
      className={({ isActive }) =>
        `inline-flex items-center gap-[7px] border-b-[3px] px-3.5 py-2.5 text-[13px] whitespace-nowrap transition-colors ${
          isActive
            ? 'border-teal bg-superficie font-bold text-navy'
            : 'border-transparent text-apagado hover:bg-teal/5 hover:text-navy'
        }`
      }
    >
      {children}
    </NavLink>
  );
}

/** Chip del usuario con su menú: Mi perfil y Cerrar sesión. Se cierra con Escape o con un clic afuera. */
function MenuUsuario({ nombre, rol }: { nombre: string; rol: string }) {
  const { salir } = useSesion();
  const navegar = useNavigate();
  const [abierto, setAbierto] = useState(false);
  const contenedor = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!abierto) return;
    function fuera(evento: MouseEvent) {
      if (!contenedor.current?.contains(evento.target as Node)) setAbierto(false);
    }
    function escape(evento: KeyboardEvent) {
      if (evento.key === 'Escape') setAbierto(false);
    }
    document.addEventListener('mousedown', fuera);
    document.addEventListener('keydown', escape);
    return () => {
      document.removeEventListener('mousedown', fuera);
      document.removeEventListener('keydown', escape);
    };
  }, [abierto]);

  /** Cierra la sesión y muestra la despedida. */
  function cerrarSesion() {
    setAbierto(false);
    navegar('/sesion-finalizada', { replace: true });
    salir();
  }

  const item = 'block w-full rounded px-3 py-2 text-left text-[13px] font-medium text-navy hover:bg-superficie-sub';
  return (
    <div ref={contenedor} className="relative">
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={abierto}
        onClick={() => setAbierto((a) => !a)}
        className="flex items-center gap-2.5 rounded-md border border-linea bg-superficie px-2.5 py-1.5 text-left hover:border-linea-oscura hover:bg-superficie-sub"
      >
        <span aria-hidden="true" className="grid h-[30px] w-[30px] place-items-center rounded bg-navy text-[11.5px] font-bold text-white">
          {iniciales(nombre)}
        </span>
        <span>
          <span className="block text-[12.5px] leading-tight font-bold text-navy">
            {nombre} <span aria-hidden="true">▾</span>
          </span>
          <span className="block text-[11px] text-apagado">{NOMBRE_ROL[rol] ?? rol}</span>
        </span>
      </button>
      {abierto && (
        <div role="menu" className="absolute top-[calc(100%+6px)] right-0 z-40 min-w-[190px] rounded-panel border border-linea-oscura bg-superficie p-1 shadow-menu">
          <Link role="menuitem" to="/perfil" onClick={() => setAbierto(false)} className={item}>
            Mi perfil
          </Link>
          <button role="menuitem" type="button" onClick={cerrarSesion} className={item}>
            Cerrar sesión
          </button>
        </div>
      )}
    </div>
  );
}

/**
 * @param puedeVer si el rol ve las alertas; si no, no pide nada
 * @returns cuántas alertas abiertas hay, o null si aún no se sabe
 */
function useAlertasAbiertas(puedeVer: boolean): number | null {
  const [abiertas, setAbiertas] = useState<number | null>(null);
  useEffect(() => {
    if (!puedeVer) return;
    let vigente = true;
    const cargar = () =>
      panelApi
        .obtener()
        .then((panel) => {
          if (vigente) setAbiertas(panel.totales.alertasAbiertas);
        })
        .catch(() => {
          // Sin contador no se pierde nada: la lista de alertas sigue disponible
        });
    cargar();
    const intervalo = window.setInterval(cargar, REFRESCO_CONTADOR_MS);
    return () => {
      vigente = false;
      window.clearInterval(intervalo);
    };
  }, [puedeVer]);
  return abiertas;
}
