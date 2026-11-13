import { useEffect, useRef, useState, type FormEvent } from 'react';
import { CLASE_BOTON_SECUNDARIO, CLASE_CAMPO, CLASE_ERROR, CLASE_ETIQUETA } from '../../comun/estilos';

/** Propiedades de DialogoTelefono. */
interface Props {
  /** Nombre de la persona que pasa a coordinador */
  nombre: string;
  /** Mensaje del backend si el último intento falló */
  error: string | null;
  guardando: boolean;
  /** Se llama con el teléfono escrito y si la persona aceptó WhatsApp */
  alGuardar: (telefono: string, aceptaWhatsapp: boolean) => void;
  alCancelar: () => void;
}

/**
 * Pide el celular con WhatsApp al pasar a COORDINADOR una cuenta que no lo tiene: los coordinadores
 * reciben los avisos de la oficina PRLAC. El número lo valida el backend y su mensaje se muestra tal cual.
 */
export function DialogoTelefono({ nombre, error, guardando, alGuardar, alCancelar }: Props) {
  const ref = useRef<HTMLDialogElement>(null);
  const [telefono, setTelefono] = useState('');
  const [aceptaWhatsapp, setAceptaWhatsapp] = useState(false);

  // showModal bloquea el resto de la página y lleva el foco al diálogo
  useEffect(() => {
    ref.current?.showModal();
  }, []);

  /** Entrega el teléfono a la tabla, que hace el cambio de rol. */
  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (telefono.trim()) alGuardar(telefono.trim(), aceptaWhatsapp);
  }

  return (
    <dialog
      ref={ref}
      aria-labelledby="telefono-titulo"
      onCancel={(e) => {
        e.preventDefault();
        if (!guardando) alCancelar();
      }}
      className="m-auto w-[calc(100%-2rem)] max-w-md rounded-xl bg-white p-0 shadow-xl backdrop:bg-slate-900/40"
    >
      <form onSubmit={enviar} className="space-y-4 p-6" noValidate>
        <h2 id="telefono-titulo" className="text-lg font-semibold text-slate-900">
          Teléfono de {nombre}
        </h2>
        <p className="text-sm text-slate-600">
          Los coordinadores reciben por WhatsApp los avisos de la oficina PRLAC en horario laboral. Para pasar a{' '}
          {nombre} a Coordinador falta su celular.
        </p>

        <label className="block">
          <span className={CLASE_ETIQUETA}>Teléfono con WhatsApp</span>
          <input
            type="tel"
            value={telefono}
            onChange={(e) => {
              setTelefono(e.target.value);
              // Sin teléfono no hay a dónde enviar WhatsApp
              if (!e.target.value.trim()) setAceptaWhatsapp(false);
            }}
            placeholder="+56 9 1234 5678"
            maxLength={30}
            required
            autoFocus
            className={CLASE_CAMPO}
          />
        </label>

        <label className="flex items-start gap-2 text-sm">
          <input
            type="checkbox"
            checked={aceptaWhatsapp}
            disabled={!telefono.trim()}
            onChange={(e) => setAceptaWhatsapp(e.target.checked)}
            className="mt-1"
          />
          <span>
            La persona aceptó recibir avisos por WhatsApp
            <span className="block text-xs text-slate-500">
              Sin esta aceptación, Meta no permite enviarle mensajes.
            </span>
          </span>
        </label>

        {error && (
          <p role="alert" className={CLASE_ERROR}>
            {error}
          </p>
        )}

        <div className="flex justify-end gap-2">
          <button type="button" onClick={alCancelar} disabled={guardando} className={CLASE_BOTON_SECUNDARIO}>
            Cancelar
          </button>
          <button
            type="submit"
            disabled={guardando || !telefono.trim()}
            className="rounded-md bg-sky-700 px-4 py-2 font-medium text-white hover:bg-sky-800 disabled:opacity-50"
          >
            {guardando ? 'Guardando…' : 'Cambiar a Coordinador'}
          </button>
        </div>
      </form>
    </dialog>
  );
}
