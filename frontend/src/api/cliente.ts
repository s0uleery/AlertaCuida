// Único punto de salida hacia el backend: agrega el token y traduce los errores.

export class ErrorApi extends Error {
  readonly estado: number;

  constructor(estado: number, mensaje: string) {
    super(mensaje);
    this.estado = estado;
  }
}

type ProveedorToken = () => string | null;
type AlSalir = () => void;

let tokenActual: ProveedorToken = () => null;
let alExpirar: AlSalir = () => {};

/** La sesión le dice al cliente de dónde sacar el token y qué hacer si el backend lo rechaza. */
export function configurarCliente(token: ProveedorToken, expirada: AlSalir) {
  tokenActual = token;
  alExpirar = expirada;
}

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
  return respuesta.status >= 500 ? 'El servidor no respondió. Intenta de nuevo.' : 'No se pudo completar la acción.';
}
