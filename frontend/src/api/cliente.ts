// Único punto de salida hacia el backend: agrega el token y traduce los errores.

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
/** Qué hacer cuando el backend rechaza el token (sesión vencida o inválida). */
type AlSalir = () => void;

let tokenActual: ProveedorToken = () => null;
let alExpirar: AlSalir = () => {};

/** La sesión le dice al cliente de dónde sacar el token y qué hacer si el backend lo rechaza. */
export function configurarCliente(token: ProveedorToken, expirada: AlSalir) {
  tokenActual = token;
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
  const cabeceras = new Headers(opciones.headers);
  cabeceras.set('Accept', 'application/json');
  if (opciones.body) {
    cabeceras.set('Content-Type', 'application/json');
  }
  const token = tokenActual();
  if (token) {
    cabeceras.set('Authorization', `Bearer ${token}`);
  }

  const respuesta = await fetch(`/api${ruta}`, { ...opciones, headers: cabeceras });
  if (!respuesta.ok) {
    if (respuesta.status === 401 && token) {
      alExpirar();
    }
    throw new ErrorApi(respuesta.status, await mensajeDeError(respuesta));
  }
  if (respuesta.status === 204) {
    return undefined as T;
  }
  return (await respuesta.json()) as T;
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
