// Dónde vive el token de acceso: solo en memoria, para que un script malicioso no lo encuentre en el
// almacenamiento del navegador. Al recargar la página se pide otro con la cookie de renovación.

/** Dónde se guarda el token de acceso mientras la página está abierta. */
export interface AlmacenToken {
  leer(): string | null;
  guardar(token: string): void;
  borrar(): void;
}

let token: string | null = null;

/** Implementación en memoria: desaparece al recargar o cerrar la pestaña. */
export const almacenToken: AlmacenToken = {
  leer: () => token,
  guardar: (nuevo) => {
    token = nuevo;
  },
  borrar: () => {
    token = null;
  },
};
