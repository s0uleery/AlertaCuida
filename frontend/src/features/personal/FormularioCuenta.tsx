import { useState, type FormEvent } from 'react';
import { ErrorApi } from '../../api/cliente';
import { CLASE_BOTON_PRINCIPAL, CLASE_CAMPO, CLASE_ERROR, CLASE_ETIQUETA } from '../../comun/estilos';
import { formatearRun, runValido } from '../../comun/run';
import { personalApi } from './personalApi';
import { ROLES } from './roles';
import type { RespuestaEnlace, SolicitudCuenta } from './tipos';

/** Formulario vacío; el rol por defecto es el de menos permisos. */
const VACIA: SolicitudCuenta = {
  run: '',
  nombre: '',
  cargo: '',
  correo: '',
  rol: 'PERSONAL',
  telefono: '',
  aceptaWhatsapp: false,
  recibeAvisos: false,
};

/** Rol que recibe por WhatsApp los avisos de la oficina PRLAC: necesita teléfono. */
const ROL_AVISOS_OFICINA = 'COORDINADOR';

/**
 * Formulario de alta de una persona del equipo. Revisa el RUN antes de enviar; el resto de las
 * reglas (RUN o correo repetido, rol válido) las revisa el backend y su mensaje se muestra tal cual.
 *
 * @param alCrear recibe el enlace generado y el nombre de la persona
 */
export function FormularioCuenta({ alCrear }: { alCrear: (enlace: RespuestaEnlace, nombre: string) => void }) {
  const [datos, setDatos] = useState<SolicitudCuenta>(VACIA);
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  /** Actualiza un solo campo del formulario. */
  function cambiar<K extends keyof SolicitudCuenta>(campo: K, valor: SolicitudCuenta[K]) {
    setDatos((anteriores) => ({ ...anteriores, [campo]: valor }));
  }

  /** Revisa el RUN, crea la cuenta y entrega su enlace a la página. */
  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (!runValido(datos.run)) {
      setError('Revisa el RUN: el dígito verificador no corresponde.');
      return;
    }
    setEnviando(true);
    setError(null);
    try {
      const enlace = await personalApi.crear(datos);
      alCrear(enlace, datos.nombre.trim());
      setDatos(VACIA);
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.');
    } finally {
      setEnviando(false);
    }
  }

  const pideTelefono = datos.rol === ROL_AVISOS_OFICINA;
  const completo =
    datos.run && datos.nombre.trim() && datos.correo.trim() && (!pideTelefono || datos.telefono.trim());

  return (
    <form onSubmit={enviar} className="grid gap-4 sm:grid-cols-2" noValidate>
      <label className="block">
        <span className={CLASE_ETIQUETA}>RUN</span>
        <input
          value={datos.run}
          onChange={(e) => cambiar('run', formatearRun(e.target.value))}
          placeholder="12.345.678-9"
          required
          className={CLASE_CAMPO}
        />
      </label>

      <label className="block">
        <span className={CLASE_ETIQUETA}>Nombre completo</span>
        <input
          value={datos.nombre}
          onChange={(e) => cambiar('nombre', e.target.value)}
          maxLength={200}
          required
          className={CLASE_CAMPO}
        />
      </label>

      <label className="block">
        <span className={CLASE_ETIQUETA}>Correo</span>
        <input
          type="email"
          value={datos.correo}
          onChange={(e) => cambiar('correo', e.target.value)}
          placeholder="nombre@municipalidad.cl"
          required
          className={CLASE_CAMPO}
        />
      </label>

      <label className="block">
        <span className={CLASE_ETIQUETA}>
          Cargo <span className="font-normal text-slate-400">(opcional)</span>
        </span>
        <input
          value={datos.cargo}
          onChange={(e) => cambiar('cargo', e.target.value)}
          placeholder="Enfermera, trabajador social…"
          maxLength={200}
          className={CLASE_CAMPO}
        />
      </label>

      <fieldset className="sm:col-span-2">
        <legend className={CLASE_ETIQUETA}>Rol</legend>
        <div className="mt-1 grid gap-2 sm:grid-cols-3">
          {ROLES.map((rol) => (
            <label
              key={rol.valor}
              className={`cursor-pointer rounded-md border px-3 py-2 ${
                datos.rol === rol.valor ? 'border-sky-600 bg-sky-50' : 'border-slate-300'
              }`}
            >
              <input
                type="radio"
                name="rol"
                value={rol.valor}
                checked={datos.rol === rol.valor}
                onChange={() => cambiar('rol', rol.valor)}
                className="mr-2"
              />
              <span className="font-medium text-slate-800">{rol.etiqueta}</span>
              <span className="block text-xs text-slate-500">{rol.descripcion}</span>
            </label>
          ))}
        </div>
      </fieldset>

      <label className="block">
        <span className={CLASE_ETIQUETA}>
          Teléfono con WhatsApp{' '}
          {!pideTelefono && <span className="font-normal text-slate-400">(opcional)</span>}
        </span>
        <input
          type="tel"
          value={datos.telefono}
          onChange={(e) =>
            setDatos((anteriores) => ({
              ...anteriores,
              telefono: e.target.value,
              // Sin teléfono no hay a dónde enviar WhatsApp
              aceptaWhatsapp: e.target.value.trim() ? anteriores.aceptaWhatsapp : false,
            }))
          }
          placeholder="+56 9 1234 5678"
          maxLength={30}
          required={pideTelefono}
          className={CLASE_CAMPO}
        />
        {pideTelefono && (
          <span className="mt-1 block text-xs text-slate-500">
            Los coordinadores reciben por WhatsApp los avisos de la oficina PRLAC en horario laboral.
          </span>
        )}
      </label>

      <label className="flex items-start gap-2 self-end text-sm">
        <input
          type="checkbox"
          checked={datos.aceptaWhatsapp}
          disabled={!datos.telefono.trim()}
          onChange={(e) => cambiar('aceptaWhatsapp', e.target.checked)}
          className="mt-1"
        />
        <span>
          La persona aceptó recibir avisos por WhatsApp
          <span className="block text-xs text-slate-500">Sin esta aceptación, Meta no permite enviarle mensajes.</span>
        </span>
      </label>

      <label className="flex items-start gap-2 text-sm sm:col-span-2">
        <input
          type="checkbox"
          checked={datos.recibeAvisos}
          onChange={(e) => cambiar('recibeAvisos', e.target.checked)}
          className="mt-1"
        />
        <span>
          Recibe avisos técnicos por correo
          <span className="block text-xs text-slate-500">
            Cuando un reloj deja de enviar datos, tiene la batería baja, no se lo ponen o envía demasiadas alertas. Solo
            en horario laboral. Se puede cambiar después en la lista de perfiles.
          </span>
        </span>
      </label>

      {error && (
        <p role="alert" className={`${CLASE_ERROR} sm:col-span-2`}>
          {error}
        </p>
      )}

      <div className="sm:col-span-2">
        <button type="submit" disabled={enviando || !completo} className={CLASE_BOTON_PRINCIPAL}>
          {enviando ? 'Creando…' : 'Crear cuenta'}
        </button>
      </div>
    </form>
  );
}
