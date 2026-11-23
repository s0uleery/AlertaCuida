import { useEffect, useRef } from 'react';

/** Clave compartida entre pestañas: usar el dashboard en una cuenta como actividad en todas. */
const CLAVE_ACTIVIDAD = 'alertacuida.ultimaActividad';
/** Cada cuánto se revisa si venció la inactividad o toca renovar. */
const REVISION_MS = 15_000;
/**
 * Espera tras una renovación fallida. Debe ser bastante menor que la gracia del servidor (10 s): si la
 * respuesta se perdió, reintentar con el token anterior pasada la gracia se trata como un robo.
 */
const REINTENTO_MS = 3_000;
/** Cada cuánto se guarda la actividad como máximo: mover el mouse dispara muchos eventos. */
const GUARDADO_MS = 5_000;
/** Cuánto antes del plazo se renueva, para que el servidor no considere inactiva la sesión. */
const MARGEN_RENOVACION_MS = 60_000;
/** Lo que cuenta como usar el dashboard. */
const EVENTOS = ['mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart'] as const;

/** Respaldo en memoria si el navegador bloquea localStorage: la actividad vale solo en esta pestaña. */
let actividadEnMemoria = 0;

/** @returns la última actividad registrada en cualquier pestaña, en milisegundos */
function leerActividad(): number {
  try {
    return Math.max(actividadEnMemoria, Number(localStorage.getItem(CLAVE_ACTIVIDAD)) || 0);
  } catch {
    return actividadEnMemoria;
  }
}

/** Guarda que hubo actividad ahora. */
export function marcarActividad() {
  actividadEnMemoria = Date.now();
  try {
    localStorage.setItem(CLAVE_ACTIVIDAD, String(actividadEnMemoria));
  } catch {
    // Sin almacenamiento: queda la copia en memoria
  }
}

/** Parámetros de {@link useControlInactividad}. */
interface Opciones {
  /** Plazo de inactividad en milisegundos, o null si no hay sesión. */
  inactividadMs: number | null;
  /** Cuándo se renovó por última vez (Date.now), lo actualiza la sesión. */
  ultimaRenovacion: { current: number };
  /** Pide tokens nuevos. Si falla por la red, se reintenta a los pocos segundos. */
  renovar: () => Promise<unknown>;
  /** Cierra la sesión porque se cumplió el plazo. */
  alVencer: () => void;
}

/**
 * Mantiene viva la sesión mientras se usa y la cierra tras el plazo sin actividad. El servidor aplica
 * el mismo plazo por su cuenta: si este código no corre (equipo suspendido), la renovación se rechaza.
 */
export function useControlInactividad({ inactividadMs, ultimaRenovacion, renovar, alVencer }: Opciones) {
  // Se leen desde refs para no reiniciar el temporizador cuando cambia la función
  const acciones = useRef({ renovar, alVencer });
  useEffect(() => {
    acciones.current = { renovar, alVencer };
  });

  useEffect(() => {
    if (inactividadMs === null) return;
    const plazoMs = inactividadMs;

    let activo = true;
    let reintento: number | undefined;
    let ultimoGuardado = 0;

    /** Registra la actividad, o cierra la sesión si el plazo ya se cumplió. */
    function alUsar() {
      const ahora = Date.now();
      if (ahora - ultimoGuardado < GUARDADO_MS) return;
      ultimoGuardado = ahora;
      // Al despertar el equipo, el primer movimiento puede llegar antes de la revisión: no debe revivir la sesión
      if (ahora - leerActividad() >= plazoMs) {
        acciones.current.alVencer();
      } else {
        marcarActividad();
      }
    }

    /** Cierra la sesión si venció la inactividad; si no, la renueva cuando se acerca el plazo. */
    function revisar() {
      window.clearTimeout(reintento);
      const ahora = Date.now();
      if (ahora - leerActividad() >= plazoMs) {
        acciones.current.alVencer();
      } else if (ahora - ultimaRenovacion.current >= plazoMs - MARGEN_RENOVACION_MS) {
        acciones.current.renovar().catch(() => {
          if (!activo) return;
          window.clearTimeout(reintento);
          reintento = window.setTimeout(revisar, REINTENTO_MS);
        });
      }
    }

    EVENTOS.forEach((evento) => window.addEventListener(evento, alUsar, { passive: true }));
    // Al volver a la pestaña o despertar el equipo se revisa de inmediato, sin esperar el temporizador
    document.addEventListener('visibilitychange', revisar);
    const temporizador = window.setInterval(revisar, REVISION_MS);
    return () => {
      activo = false;
      window.clearTimeout(reintento);
      EVENTOS.forEach((evento) => window.removeEventListener(evento, alUsar));
      document.removeEventListener('visibilitychange', revisar);
      window.clearInterval(temporizador);
    };
  }, [inactividadMs, ultimaRenovacion]);
}
