import { useState, type FormEvent } from 'react';
import { ErrorApi } from '../../api/cliente';
import { useConfirmar } from '../../comun/Confirmacion';
import {
  CLASE_BOTON_PRINCIPAL,
  CLASE_BOTON_SECUNDARIO,
  CLASE_CAMPO,
  CLASE_ERROR,
  CLASE_ETIQUETA,
} from '../../comun/estilos';
import { formatearFechaHora } from '../../comun/fechas';
import { Pestanas } from '../../comun/ui/Pestanas';
import { useDatos } from '../../comun/useDatos';
import { useSesion } from '../sesion/SesionContext';
import { pacientesApi } from './pacientesApi';
import type { Contacto, DatosContacto, GravedadContacto, TipoContacto, Tramo } from './tipos';

/** Las tres gravedades, en el orden de las pestañas. */
const GRAVEDADES: GravedadContacto[] = ['LEVE', 'MEDIA', 'GRAVE'];

/** Cómo se muestra cada gravedad. */
const NOMBRE_GRAVEDAD: Record<GravedadContacto, string> = { LEVE: 'Leve', MEDIA: 'Media', GRAVE: 'Grave' };

/** Único tipo de contacto que admite parentesco (familiares, vecinos y personas cercanas). */
const TERCERO_SIGNIFICATIVO = 'TERCERO_SIGNIFICATIVO';

/** Formulario vacío para un contacto nuevo: recibe avisos de las tres gravedades. */
const VACIO: DatosContacto = {
  nombre: '',
  whatsapp: null,
  correo: null,
  tipo: TERCERO_SIGNIFICATIVO,
  parentesco: null,
  cuidadorPrincipal: false,
  horaInicio: null,
  horaFin: null,
  gravedades: GRAVEDADES,
};

/** Propiedades de ContactosDelUsuario. */
interface Props {
  idPaciente: string;
}

/**
 * Lista de contactos del usuario, con una pestaña por gravedad (leve, media, grave): cada una muestra a quién
 * avisa el reloj en las alertas de esa gravedad y en qué orden, y un contacto puede estar en varias. Con
 * USUARIOS:EDITAR se pueden agregar, editar, ordenar cada gravedad, desactivar y registrar su opt-in de
 * WhatsApp. Avisa si hay horas del día sin nadie disponible (las alertas medias y graves avisan a toda hora).
 */
export function ContactosDelUsuario({ idPaciente }: Props) {
  const { puede } = useSesion();
  const confirmar = useConfirmar();
  const { datos, error, recargar } = useDatos(() => pacientesApi.contactos(idPaciente), idPaciente);
  const { datos: tipos } = useDatos(() => pacientesApi.tiposContacto(), 'tipos');
  const [errorCambio, setErrorCambio] = useState<string | null>(null);
  // null = sin formulario abierto; 'nuevo' = agregando; si no, el id del contacto que se edita
  const [editando, setEditando] = useState<string | null>(null);
  const [gravedad, setGravedad] = useState<GravedadContacto>('GRAVE');
  const editable = puede('USUARIOS:EDITAR');

  /** Ejecuta un cambio en el backend y recarga la lista; devuelve si salió bien. */
  async function cambiar(accion: () => Promise<unknown>): Promise<boolean> {
    setErrorCambio(null);
    try {
      await accion();
      recargar();
      return true;
    } catch (e) {
      setErrorCambio(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.');
      return false;
    }
  }

  if (error) return <p className={CLASE_ERROR}>{error}</p>;
  if (!datos || !tipos) return <p className="text-apagado">Cargando…</p>;

  const activos = datos.contactos.filter((contacto) => contacto.activo);
  const inactivos = datos.contactos.filter((contacto) => !contacto.activo);
  const nombreTipo = (codigo: string) => tipos.find((tipo) => tipo.codigo === codigo)?.nombre ?? codigo;
  /** Los contactos de esa gravedad, del primero al último. */
  const deGravedad = (g: GravedadContacto) =>
    activos
      .filter((contacto) => contacto.prioridades[g] !== undefined)
      .sort((a, b) => (a.prioridades[g] ?? 0) - (b.prioridades[g] ?? 0));
  const lista = deGravedad(gravedad);

  /** Sube o baja un contacto un lugar en la lista de la gravedad elegida. */
  function mover(indice: number, salto: -1 | 1) {
    const orden = lista.map((contacto) => contacto.id);
    [orden[indice], orden[indice + salto]] = [orden[indice + salto], orden[indice]];
    cambiar(() => pacientesApi.reordenarContactos(idPaciente, gravedad, orden));
  }

  return (
    <div className="space-y-4">
      <AvisoCobertura sinCobertura={datos.sinCobertura} />
      {errorCambio && (
        <p role="alert" className={CLASE_ERROR}>
          {errorCambio}
        </p>
      )}

      <Pestanas
        etiqueta="Gravedad de la alerta"
        activa={gravedad}
        alCambiar={setGravedad}
        pestanas={GRAVEDADES.map((g) => ({
          clave: g,
          texto: `${NOMBRE_GRAVEDAD[g]} (${deGravedad(g).length})`,
        }))}
      />
      <p className="text-sm text-apagado">
        En una alerta {NOMBRE_GRAVEDAD[gravedad].toLowerCase()} el reloj avisa a estos contactos, en este orden.
        {gravedad !== 'GRAVE' && ' Si la alerta se agrava, sigue con la lista de grave desde el primero.'}
      </p>

      {lista.length === 0 ? (
        <p className="text-sm text-apagado">
          {activos.length === 0
            ? 'Sin contactos. El reloj no tendrá a quién avisar. El primero recibe avisos de las tres gravedades.'
            : 'Ningún contacto recibe avisos de esta gravedad.'}
        </p>
      ) : (
        <ol className="divide-y divide-linea">
          {lista.map((contacto, indice) =>
            editando === contacto.id ? (
              <li key={contacto.id} className="py-3">
                <FormularioContacto
                  inicial={datosDe(contacto)}
                  tipos={tipos}
                  compartido={contacto.otrosPacientes}
                  alCancelar={() => setEditando(null)}
                  alGuardar={async (datosNuevos) => {
                    const listo = await cambiar(() =>
                      pacientesApi.editarContacto(idPaciente, contacto.id, datosNuevos),
                    );
                    if (listo) setEditando(null);
                  }}
                />
              </li>
            ) : (
              <li key={contacto.id} className="flex flex-wrap items-start justify-between gap-3 py-3">
                <FilaContacto contacto={contacto} nombreTipo={nombreTipo(contacto.tipo)} gravedad={gravedad} />
                {editable && (
                  <div className="flex flex-wrap items-center gap-2 text-sm">
                    <button
                      onClick={() => mover(indice, -1)}
                      disabled={indice === 0}
                      className="px-1 text-apagado disabled:opacity-30"
                      aria-label={`Subir a ${contacto.nombre}`}
                    >
                      ↑
                    </button>
                    <button
                      onClick={() => mover(indice, 1)}
                      disabled={indice === lista.length - 1}
                      className="px-1 text-apagado disabled:opacity-30"
                      aria-label={`Bajar a ${contacto.nombre}`}
                    >
                      ↓
                    </button>
                    {contacto.whatsapp && (
                      <button
                        onClick={async () => {
                          const titulo = contacto.optInVigente
                            ? `¿Registrar que ${contacto.nombre} ya no quiere recibir WhatsApp?`
                            : `¿Registrar que ${contacto.nombre} aceptó recibir avisos por WhatsApp?`;
                          if (await confirmar({ titulo, textoConfirmar: 'Registrar' })) {
                            cambiar(() =>
                              pacientesApi.registrarOptIn(idPaciente, contacto.id, !contacto.optInVigente),
                            );
                          }
                        }}
                        className="text-teal-oscuro hover:underline"
                      >
                        {contacto.optInVigente ? 'Revocar WhatsApp' : 'Aceptó WhatsApp'}
                      </button>
                    )}
                    <button onClick={() => setEditando(contacto.id)} className="text-teal-oscuro hover:underline">
                      Editar
                    </button>
                    <button
                      onClick={async () => {
                        const confirmado = await confirmar({
                          titulo: `¿Desactivar a ${contacto.nombre}?`,
                          mensaje: 'Sus avisos quedan en el historial.',
                          textoConfirmar: 'Desactivar',
                          peligro: true,
                        });
                        if (confirmado) cambiar(() => pacientesApi.desactivarContacto(idPaciente, contacto.id));
                      }}
                      className="text-magenta hover:underline"
                    >
                      Desactivar
                    </button>
                  </div>
                )}
              </li>
            ),
          )}
        </ol>
      )}

      {editable &&
        (editando === 'nuevo' ? (
          <FormularioContacto
            inicial={VACIO}
            tipos={tipos}
            compartido={0}
            alCancelar={() => setEditando(null)}
            alGuardar={async (datosNuevos) => {
              if (await cambiar(() => pacientesApi.agregarContacto(idPaciente, datosNuevos))) setEditando(null);
            }}
          />
        ) : (
          <button onClick={() => setEditando('nuevo')} className={CLASE_BOTON_SECUNDARIO}>
            Agregar contacto
          </button>
        ))}

      {inactivos.length > 0 && (
        <details className="text-sm">
          <summary className="cursor-pointer text-apagado">Desactivados ({inactivos.length})</summary>
          <ul className="mt-2 divide-y divide-linea">
            {inactivos.map((contacto) => (
              <li key={contacto.id} className="flex items-center justify-between gap-2 py-2">
                <span className="text-apagado">
                  {contacto.nombre} · {nombreTipo(contacto.tipo)}
                </span>
                {editable && (
                  <Reactivar
                    nombre={contacto.nombre}
                    alReactivar={(gravedades) =>
                      cambiar(() => pacientesApi.reactivarContacto(idPaciente, contacto.id, gravedades))
                    }
                  />
                )}
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}

/** Aviso en rojo con los tramos del día sin ningún contacto disponible. */
function AvisoCobertura({ sinCobertura }: { sinCobertura: Tramo[] }) {
  if (sinCobertura.length === 0) {
    return <p className="text-sm text-verde-texto">Hay al menos un contacto disponible a toda hora.</p>;
  }
  const tramos = sinCobertura.map((tramo) =>
    tramo.inicio === tramo.fin ? 'todo el día' : `${tramo.inicio} a ${tramo.fin}`,
  );
  return (
    <p role="status" className={CLASE_ERROR}>
      Sin contacto disponible: {tramos.join(', ')}. Las alertas medias y graves avisan a toda hora; agrega un
      contacto para esas horas, con correo o con WhatsApp aceptado.
    </p>
  );
}

/** Propiedades de FilaContacto. */
interface PropsFila {
  contacto: Contacto;
  nombreTipo: string;
  /** La pestaña en que se muestra: su número es el lugar en esa lista */
  gravedad: GravedadContacto;
}

/** Datos de un contacto activo: lugar en la gravedad, nombre, tipo, otras gravedades, medios, horario y opt-in. */
function FilaContacto({ contacto, nombreTipo, gravedad }: PropsFila) {
  const horario = contacto.horaInicio ? `${contacto.horaInicio} a ${contacto.horaFin}` : 'Todo el día';
  const otras = GRAVEDADES.filter((g) => g !== gravedad && contacto.prioridades[g] !== undefined).map(
    (g) => `${NOMBRE_GRAVEDAD[g].toLowerCase()} (${contacto.prioridades[g]}.º)`,
  );
  return (
    <div className="flex gap-3">
      <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-teal-tinte text-sm font-semibold text-teal-oscuro">
        {contacto.prioridades[gravedad]}
      </span>
      <div className="space-y-0.5">
        <p className="font-medium text-navy">
          {contacto.nombre}
          {contacto.cuidadorPrincipal && (
            <span className="ml-2 rounded bg-naranja-tinte px-1.5 py-0.5 text-xs font-medium text-naranja-texto">
              Cuidador principal
            </span>
          )}
        </p>
        <p className="text-sm text-apagado">
          {nombreTipo}
          {contacto.parentesco && ` (${contacto.parentesco})`} · {horario}
        </p>
        <p className="text-xs text-apagado">
          {otras.length > 0 ? `También en alertas ${otras.join(' y ')}.` : 'Solo en esta gravedad.'}
        </p>
        <p className="text-sm text-apagado">
          {contacto.whatsapp && (
            <span className="mr-3">
              WhatsApp {contacto.whatsapp}{' '}
              {contacto.optInVigente ? (
                <span className="text-verde-texto">(aceptado el {formatearFechaHora(contacto.fechaOptIn!)})</span>
              ) : (
                <span className="text-naranja-texto">(sin aceptar: no se le envía)</span>
              )}
            </span>
          )}
          {contacto.correo && <span>Correo {contacto.correo}</span>}
        </p>
        {contacto.canales.length === 0 && (
          <p className="text-sm text-magenta">No se le puede avisar: falta el correo o que acepte WhatsApp.</p>
        )}
        {contacto.otrosPacientes > 0 && (
          <p className="text-xs text-apagado">
            También es contacto de {contacto.otrosPacientes}{' '}
            {contacto.otrosPacientes === 1 ? 'usuario más' : 'usuarios más'}.
          </p>
        )}
      </div>
    </div>
  );
}

/** @returns los datos del contacto para llenar el formulario */
function datosDe(contacto: Contacto): DatosContacto {
  return {
    nombre: contacto.nombre,
    whatsapp: contacto.whatsapp,
    correo: contacto.correo,
    tipo: contacto.tipo,
    parentesco: contacto.parentesco,
    cuidadorPrincipal: contacto.cuidadorPrincipal,
    horaInicio: contacto.horaInicio,
    horaFin: contacto.horaFin,
    gravedades: GRAVEDADES.filter((g) => contacto.prioridades[g] !== undefined),
  };
}

/** Casillas Leve / Media / Grave para elegir de qué alertas recibe avisos un contacto. */
function CasillasGravedad({
  elegidas,
  alCambiar,
}: {
  elegidas: GravedadContacto[];
  alCambiar: (gravedades: GravedadContacto[]) => void;
}) {
  return (
    <div className="flex flex-wrap gap-4 text-sm">
      {GRAVEDADES.map((g) => (
        <label key={g} className="flex items-center gap-2">
          <input
            type="checkbox"
            checked={elegidas.includes(g)}
            onChange={(e) =>
              alCambiar(GRAVEDADES.filter((otra) => (otra === g ? e.target.checked : elegidas.includes(otra))))
            }
          />
          {NOMBRE_GRAVEDAD[g]}
        </label>
      ))}
    </div>
  );
}

/** Botón para reactivar un contacto: primero se eligen las gravedades en que vuelve, al final de cada una. */
function Reactivar({
  nombre,
  alReactivar,
}: {
  nombre: string;
  alReactivar: (gravedades: GravedadContacto[]) => Promise<boolean>;
}) {
  const [abierto, setAbierto] = useState(false);
  const [gravedades, setGravedades] = useState<GravedadContacto[]>(GRAVEDADES);

  if (!abierto) {
    return (
      <button onClick={() => setAbierto(true)} className="text-teal-oscuro hover:underline">
        Reactivar
      </button>
    );
  }
  return (
    <div className="flex flex-wrap items-center gap-3" role="group" aria-label={`Reactivar a ${nombre}`}>
      <CasillasGravedad elegidas={gravedades} alCambiar={setGravedades} />
      <button
        disabled={gravedades.length === 0}
        onClick={async () => {
          if (await alReactivar(gravedades)) setAbierto(false);
        }}
        className="text-teal-oscuro hover:underline disabled:opacity-40"
      >
        Reactivar
      </button>
      <button onClick={() => setAbierto(false)} className="text-apagado hover:underline">
        Cancelar
      </button>
    </div>
  );
}

/** Propiedades de FormularioContacto. */
interface PropsFormulario {
  inicial: DatosContacto;
  tipos: TipoContacto[];
  /** De cuántos otros usuarios es contacto (para advertir que el cambio los afecta) */
  compartido: number;
  alCancelar: () => void;
  alGuardar: (datos: DatosContacto) => Promise<void>;
}

/** Formulario para agregar o editar un contacto. Las reglas las revisa el backend. */
function FormularioContacto({ inicial, tipos, compartido, alCancelar, alGuardar }: PropsFormulario) {
  const [datos, setDatos] = useState(inicial);
  const [todoElDia, setTodoElDia] = useState(inicial.horaInicio === null);
  const [guardando, setGuardando] = useState(false);

  /** Cambia un campo; un texto vacío se envía como null. */
  function campo<K extends keyof DatosContacto>(nombre: K, valor: DatosContacto[K]) {
    setDatos((anteriores) => ({ ...anteriores, [nombre]: valor === '' ? null : valor }));
  }

  /** Envía los datos, con el horario vacío si es todo el día y sin parentesco si no es tercero significativo. */
  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    setGuardando(true);
    await alGuardar({
      ...datos,
      parentesco: datos.tipo === TERCERO_SIGNIFICATIVO ? datos.parentesco : null,
      horaInicio: todoElDia ? null : datos.horaInicio,
      horaFin: todoElDia ? null : datos.horaFin,
    });
    setGuardando(false);
  }

  return (
    <form onSubmit={enviar} className="space-y-3 rounded-lg border border-linea p-4">
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block">
          <span className={CLASE_ETIQUETA}>Nombre</span>
          <input
            required
            maxLength={120}
            value={datos.nombre}
            onChange={(e) => setDatos({ ...datos, nombre: e.target.value })}
            className={CLASE_CAMPO}
          />
        </label>
        <label className="block">
          <span className={CLASE_ETIQUETA}>Tipo</span>
          <select value={datos.tipo} onChange={(e) => campo('tipo', e.target.value)} className={CLASE_CAMPO}>
            {tipos.map((tipo) => (
              <option key={tipo.codigo} value={tipo.codigo}>
                {tipo.nombre}
              </option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className={CLASE_ETIQUETA}>WhatsApp</span>
          <input
            type="tel"
            maxLength={30}
            placeholder="+56 9 1234 5678"
            value={datos.whatsapp ?? ''}
            onChange={(e) => campo('whatsapp', e.target.value)}
            className={CLASE_CAMPO}
          />
        </label>
        <label className="block">
          <span className={CLASE_ETIQUETA}>Correo</span>
          <input
            type="email"
            maxLength={254}
            value={datos.correo ?? ''}
            onChange={(e) => campo('correo', e.target.value)}
            className={CLASE_CAMPO}
          />
        </label>
        {datos.tipo === TERCERO_SIGNIFICATIVO && (
          <label className="block">
            <span className={CLASE_ETIQUETA}>Parentesco (opcional)</span>
            <input
              maxLength={60}
              placeholder="Hija, sobrino…"
              value={datos.parentesco ?? ''}
              onChange={(e) => campo('parentesco', e.target.value)}
              className={CLASE_CAMPO}
            />
          </label>
        )}
      </div>
      <p className="text-xs text-apagado">
        Ingresa al menos un WhatsApp o un correo. El SAMU no se agrega: el mensaje de una alerta grave ya pide llamar
        al 131.
      </p>

      <fieldset className="space-y-2">
        <legend className={CLASE_ETIQUETA}>Recibe avisos de las alertas</legend>
        <CasillasGravedad elegidas={datos.gravedades} alCambiar={(gravedades) => setDatos({ ...datos, gravedades })} />
        <p className="text-xs text-apagado">
          Una gravedad nueva lo deja al final de esa lista; el orden se cambia en cada pestaña. Ninguna gravedad
          puede quedar sin contactos.
        </p>
      </fieldset>

      <fieldset className="space-y-2">
        <legend className={CLASE_ETIQUETA}>Horario en que puede recibir avisos</legend>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={todoElDia} onChange={(e) => setTodoElDia(e.target.checked)} />
          Todo el día
        </label>
        {!todoElDia && (
          <div className="flex items-center gap-2 text-sm">
            <input
              type="time"
              required
              value={datos.horaInicio ?? ''}
              onChange={(e) => campo('horaInicio', e.target.value)}
              className={`${CLASE_CAMPO} mt-0 w-auto`}
              aria-label="Desde"
            />
            a
            <input
              type="time"
              required
              value={datos.horaFin ?? ''}
              onChange={(e) => campo('horaFin', e.target.value)}
              className={`${CLASE_CAMPO} mt-0 w-auto`}
              aria-label="Hasta"
            />
            <span className="text-apagado">(si termina antes de empezar, cruza la medianoche)</span>
          </div>
        )}
      </fieldset>

      <label className="flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          checked={datos.cuidadorPrincipal}
          onChange={(e) => setDatos({ ...datos, cuidadorPrincipal: e.target.checked })}
        />
        Es el cuidador principal (si ya hay otro, se le quita)
      </label>

      {compartido > 0 && (
        <p className="text-sm text-naranja-texto">
          Nombre, WhatsApp y correo también cambiarán para {compartido === 1 ? 'el otro usuario' : 'los otros usuarios'}{' '}
          de esta persona. Si cambia el WhatsApp, deberá volver a aceptar recibir mensajes.
        </p>
      )}

      <div className="flex gap-2">
        <button type="submit" disabled={guardando} className={CLASE_BOTON_PRINCIPAL}>
          {guardando ? 'Guardando…' : 'Guardar'}
        </button>
        <button type="button" onClick={alCancelar} className={CLASE_BOTON_SECUNDARIO}>
          Cancelar
        </button>
      </div>
    </form>
  );
}
