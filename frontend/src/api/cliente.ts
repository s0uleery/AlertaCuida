// Único punto de salida hacia el backend: agrega el token, lo renueva si venció y traduce los errores.

/** Respuesta de error del backend. El mensaje es el "detail" del ProblemDetail, listo para mostrar. */
export class ErrorApi extends Error {
  /** Código HTTP: 400 dato inválido, 401 sin sesión, 403 sin permiso, 409 duplicado… */
  readonly estado: number;

  constructor(estado: number, mensaje: string) {
    super(mensaje);
    this.estado = estado;
  }
}

/** Función que entrega el token actual, o null si no hay sesión. */
type ProveedorToken = () => string | null;
/** Pide un token nuevo. Responde false si la sesión terminó; lanza un error si no se pudo saber. */
type Renovador = () => Promise<boolean>;
/** Qué hacer cuando la sesión terminó y no se puede renovar. */
type AlSalir = () => void;

/** Rutas públicas: no llevan token, porque uno vencido haría que Spring Security rechazara la petición. */
const RUTAS_PUBLICAS = new Set(['/auth/login', '/auth/renovar', '/auth/salir', '/auth/activar']);

let tokenActual: ProveedorToken = () => null;
let renovarToken: Renovador = async () => false;
let alExpirar: AlSalir = () => {};

/** La sesión le dice al cliente de dónde sacar el token, cómo renovarlo y qué hacer si ya no se puede. */
export function configurarCliente(token: ProveedorToken, renovar: Renovador, expirada: AlSalir) {
  tokenActual = token;
  renovarToken = renovar;
  alExpirar = expirada;
}

/**
 * Llama al backend y devuelve la respuesta ya convertida desde JSON.
 *
 * @param ruta     sin el prefijo /api (ej. "/auth/login")
 * @param opciones las de fetch: método, cuerpo…
 * @returns el cuerpo de la respuesta; undefined si el backend responde 204 (sin cuerpo)
 * @throws ErrorApi si el backend responde con error
 */
export async function pedir<T>(ruta: string, opciones: RequestInit = {}): Promise<T> {
  const conToken = !RUTAS_PUBLICAS.has(ruta);
  let respuesta = await enviar(ruta, opciones, conToken);
  if (respuesta.status === 401 && conToken && tokenActual()) {
    if (await renovarToken()) {
      respuesta = await enviar(ruta, opciones, conToken);
    }
    if (respuesta.status === 401) {
      alExpirar();
    }
  }
  if (!respuesta.ok) {
    throw new ErrorApi(respuesta.status, await mensajeDeError(respuesta));
  }
  if (respuesta.status === 204) {
    return undefined as T;
  }
  return (await respuesta.json()) as T;
}

/** Hace la petición con las cabeceras de la API y, si corresponde, con el token actual. */
function enviar(ruta: string, opciones: RequestInit, conToken: boolean): Promise<Response> {
  const cabeceras = new Headers(opciones.headers);
  cabeceras.set('Accept', 'application/json');
  if (opciones.body) {
    cabeceras.set('Content-Type', 'application/json');
  }
  const token = conToken ? tokenActual() : null;
  if (token) {
    cabeceras.set('Authorization', `Bearer ${token}`);
  }
  return fetch(`/api${ruta}`, { ...opciones, headers: cabeceras });
}

/** Los errores del backend vienen como ProblemDetail ({ detail: "..." }). */
async function mensajeDeError(respuesta: Response): Promise<string> {
  try {
    const cuerpo = await respuesta.json();
    if (typeof cuerpo?.detail === 'string') {
      return cuerpo.detail;
    }
  } catch {
    // Sin cuerpo JSON: se usa el mensaje genérico
  }
  // 404 o 405 sin mensaje propio: el backend todavía no tiene ese endpoint
  if (respuesta.status === 404 || respuesta.status === 405) {
    return 'Esta función todavía no está disponible en el servidor.';
  }
  return respuesta.status >= 500 ? 'El servidor no respondió. Intenta de nuevo.' : 'No se pudo completar la acción.';
}
