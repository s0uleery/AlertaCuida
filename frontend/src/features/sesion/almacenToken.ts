// Dónde vive el token. Por ahora en sessionStorage (sobrevive a recargar, no a cerrar la pestaña).
// En 3.2.4 pasa a memoria + renovación con cookie: solo cambia este archivo.

const CLAVE = 'alertacuida.token';

export interface AlmacenToken {
  leer(): string | null;
  guardar(token: string): void;
  borrar(): void;
}

export const almacenToken: AlmacenToken = {
  leer: () => {
    try {
      return sessionStorage.getItem(CLAVE);
    } catch {
      return null;
    }
  },
  guardar: (token) => {
    try {
      sessionStorage.setItem(CLAVE, token);
    } catch {
      // Sin almacenamiento: la sesión dura hasta recargar
    }
  },
  borrar: () => {
    try {
      sessionStorage.removeItem(CLAVE);
    } catch {
      // Nada que borrar
    }
  },
};
