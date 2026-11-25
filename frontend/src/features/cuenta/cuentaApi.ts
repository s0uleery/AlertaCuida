import { pedir } from '../../api/cliente';

/** Lo que la persona ve de su propia cuenta (GET /api/mi-cuenta). */
export interface MiCuenta {
  nombre: string;
  correo: string | null;
  /** Celular en formato +569…; null si no tiene */
  telefono: string | null;
  /** Si recibe avisos por WhatsApp */
  whatsapp: boolean;
}

/** Llamadas al backend sobre la cuenta propia. No exigen permisos: siempre es la cuenta de la sesión. */
export const cuentaApi = {
  ver: () => pedir<MiCuenta>('/mi-cuenta'),

  /** Cambia el teléfono y si recibe WhatsApp. El correo le sigue llegando siempre. */
  cambiarWhatsapp: (telefono: string, aceptaWhatsapp: boolean) =>
    pedir<void>('/mi-cuenta/whatsapp', { method: 'PUT', body: JSON.stringify({ telefono, aceptaWhatsapp }) }),
};
