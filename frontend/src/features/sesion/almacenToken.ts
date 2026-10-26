// Dónde vive el token. Por ahora en sessionStorage (sobrevive a recargar, no a cerrar la pestaña).
// Más adelante pasa a memoria + renovación con cookie: solo cambia este archivo.

const CLAVE = 'alertacuida.token';

/** Dónde se guarda el token entre recargas de la página. */
export interface AlmacenToken {
  leer(): string | null;
  guardar(token: string): void;
  borrar(): void;
}

/** Implementación con sessionStorage. Si el navegador lo bloquea, no falla: la sesión dura hasta recargar. */
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
