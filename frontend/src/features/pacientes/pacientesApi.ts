import { pedir } from '../../api/cliente';
import type { EstadoReloj } from '../relojes/tipos';
import type {
  AjustesClinicos,
  Condicion,
  ConfiguracionAplicada,
  DatosContacto,
  DatosPaciente,
  DetallePaciente,
  ListaContactos,
  PerfilClinico,
  ResumenPaciente,
  TipoCondicion,
  TipoContacto,
  TokenReloj,
  Umbral,
  VariableUmbral,
  ZonaSegura,
} from './tipos';

/**
 * Llamadas al backend para pacientes, sus condiciones, perfil clínico, contactos y reloj.
 * Exigen permisos de USUARIOS.
 */
export const pacientesApi = {
  /** Pacientes con su edad y reloj actual. */
  listar: (incluirBajas: boolean) => pedir<ResumenPaciente[]>(`/pacientes?incluirBajas=${incluirBajas}`),

  /** Ficha con historial de relojes. */
  detalle: (id: string) => pedir<DetallePaciente>(`/pacientes/${id}`),

  /** Registra un paciente; devuelve su identificador. */
  registrar: (datos: DatosPaciente) =>
    pedir<{ id: string }>('/pacientes', { method: 'POST', body: JSON.stringify(datos) }),

  /** Reemplaza su identidad. */
  editar: (id: string, datos: DatosPaciente) =>
    pedir<void>(`/pacientes/${id}`, { method: 'PUT', body: JSON.stringify(datos) }),

  /** Baja con fecha de hoy; cierra su reloj. */
  darDeBaja: (id: string) => pedir<void>(`/pacientes/${id}/baja`, { method: 'POST' }),

  /** Enfermedades y luego situaciones, por orden alfabético. */
  condiciones: (id: string) => pedir<Condicion[]>(`/pacientes/${id}/condiciones`),

  /** Agrega una enfermedad o situación; responde 409 si ya la tiene. */
  agregarCondicion: (id: string, tipo: TipoCondicion, descripcion: string) =>
    pedir<{ id: string }>(`/pacientes/${id}/condiciones`, {
      method: 'POST',
      body: JSON.stringify({ tipo, descripcion }),
    }),

  /** Quita una enfermedad o situación. */
  quitarCondicion: (id: string, idCondicion: string) =>
    pedir<void>(`/pacientes/${id}/condiciones/${idCondicion}`, { method: 'DELETE' }),

  /** Módulos, opciones y umbrales que rigen hoy, con los valores base donde no hay ajustes. */
  perfilClinico: (id: string) => pedir<PerfilClinico>(`/pacientes/${id}/perfil-clinico`),

  /** Reemplaza módulos y opciones; si no cambia nada, no crea otra versión. */
  cambiarAjustes: (id: string, ajustes: AjustesClinicos) =>
    pedir<void>(`/pacientes/${id}/perfil-clinico/ajustes`, { method: 'PUT', body: JSON.stringify(ajustes) }),

  /** Ajusta el umbral de una variable para el paciente. */
  ajustarUmbral: (id: string, variable: VariableUmbral, umbral: Umbral) =>
    pedir<void>(`/pacientes/${id}/perfil-clinico/umbrales/${variable}`, {
      method: 'PUT',
      body: JSON.stringify(umbral),
    }),

  /** Vuelve al umbral base, sin borrar el historial. */
  volverAlUmbralBase: (id: string, variable: VariableUmbral) =>
    pedir<void>(`/pacientes/${id}/perfil-clinico/umbrales/${variable}`, { method: 'DELETE' }),

  /** Define o cambia la zona segura (no la activa: eso va en los ajustes); si es igual, no crea otra versión. */
  definirZonaSegura: (id: string, zona: ZonaSegura) =>
    pedir<void>(`/pacientes/${id}/perfil-clinico/zona-segura`, { method: 'PUT', body: JSON.stringify(zona) }),

  /** Tipos de contacto que se pueden elegir. */
  tiposContacto: () => pedir<TipoContacto[]>('/tipos-contacto'),

  /** Activos por prioridad y luego desactivados, con las horas sin cobertura. */
  contactos: (id: string) => pedir<ListaContactos>(`/pacientes/${id}/contactos`),

  /** Agrega un contacto al final de la lista; si la persona ya existe, se reutiliza. */
  agregarContacto: (id: string, datos: DatosContacto) =>
    pedir<{ id: string }>(`/pacientes/${id}/contactos`, { method: 'POST', body: JSON.stringify(datos) }),

  /** Reemplaza los datos del contacto; nombre y medios cambian en todos sus usuarios. */
  editarContacto: (id: string, idContacto: string, datos: DatosContacto) =>
    pedir<void>(`/pacientes/${id}/contactos/${idContacto}`, { method: 'PUT', body: JSON.stringify(datos) }),

  /** Registra que aceptó (o revocó) recibir WhatsApp. */
  registrarOptIn: (id: string, idContacto: string, aceptado: boolean) =>
    pedir<void>(`/pacientes/${id}/contactos/${idContacto}/opt-in`, {
      method: 'PUT',
      body: JSON.stringify({ aceptado }),
    }),

  /** Saca al contacto de la lista sin borrarlo. */
  desactivarContacto: (id: string, idContacto: string) =>
    pedir<void>(`/pacientes/${id}/contactos/${idContacto}/desactivar`, { method: 'POST' }),

  /** Vuelve a poner al contacto en la lista, al final. */
  reactivarContacto: (id: string, idContacto: string) =>
    pedir<void>(`/pacientes/${id}/contactos/${idContacto}/reactivar`, { method: 'POST' }),

  /** Nuevo orden de aviso; debe incluir a todos los activos. */
  reordenarContactos: (id: string, orden: string[]) =>
    pedir<void>(`/pacientes/${id}/contactos/orden`, { method: 'PUT', body: JSON.stringify({ orden }) }),

  /** Entrega un reloj a un paciente que no tiene. */
  vincular: (id: string, idDispositivo: string) =>
    pedir<TokenReloj>(`/pacientes/${id}/reloj`, { method: 'POST', body: JSON.stringify({ idDispositivo }) }),

  /** Cambia su reloj por otro; el anterior puede quedar en otro estado (ej. EN_REPARACION). */
  reemplazar: (id: string, idDispositivo: string, estadoRelojAnterior: EstadoReloj | null) =>
    pedir<TokenReloj>(`/pacientes/${id}/reloj/reemplazo`, {
      method: 'POST',
      body: JSON.stringify({ idDispositivo, estadoRelojAnterior }),
    }),

  /** Le retira el reloj; su token deja de servir. */
  desvincular: (id: string) => pedir<void>(`/pacientes/${id}/reloj`, { method: 'DELETE' }),

  /** Otro token para el mismo reloj; el anterior deja de servir. */
  nuevoToken: (id: string) => pedir<TokenReloj>(`/pacientes/${id}/reloj/token`, { method: 'POST' }),

  /** Si el reloj ya aplica la configuración vigente (contactos, umbrales, calendario). */
  configuracionReloj: (id: string) => pedir<ConfiguracionAplicada>(`/pacientes/${id}/reloj/configuracion`),
};
