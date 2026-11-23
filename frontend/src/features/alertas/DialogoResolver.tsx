import { useEffect, useRef, useState, type FormEvent } from 'react';
import { CLASE_BOTON_SECUNDARIO, CLASE_CAMPO, CLASE_ERROR, CLASE_ETIQUETA } from '../../comun/estilos';
import { CLASIFICACION } from './etiquetas';
import type { ClasificacionAlerta } from './tipos';

/** Propiedades de DialogoResolver. */
interface Props {
  /** Folio de la alerta, para el título */
  numero: number;
  /** Las de salud se clasifican; las técnicas no */
  esSalud: boolean;
  /** Mensaje del backend si el último intento falló */
  error: string | null;
  guardando: boolean;
  /** Se llama con la clasificación (null en las técnicas) y las observaciones escritas */
  alResolver: (clasificacion: ClasificacionAlerta | null, observaciones: string) => void;
  alCancelar: () => void;
}

/**
 * Cierra una alerta. Las observaciones son obligatorias siempre; las alertas de salud además se clasifican.
 * Una positiva queda en seguimiento hasta cerrarlo. En las de salud, el reloj deja de avisar a los contactos.
 */
export function DialogoResolver({ numero, esSalud, error, guardando, alResolver, alCancelar }: Props) {
  const ref = useRef<HTMLDialogElement>(null);
  const [clasificacion, setClasificacion] = useState<ClasificacionAlerta | null>(null);
  const [observaciones, setObservaciones] = useState('');
  const completo = observaciones.trim() !== '' && (!esSalud || clasificacion !== null);

  // showModal bloquea el resto de la página y lleva el foco al diálogo
  useEffect(() => {
    ref.current?.showModal();
  }, []);

  /** Entrega lo escrito a la ficha, que llama al backend. */
  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (completo) alResolver(esSalud ? clasificacion : null, observaciones.trim());
  }

  return (
    <dialog
      ref={ref}
      aria-labelledby="resolver-titulo"
      onCancel={(e) => {
        e.preventDefault();
        if (!guardando) alCancelar();
      }}
      className="m-auto w-[calc(100%-2rem)] max-w-lg rounded-xl bg-white p-0 shadow-xl backdrop:bg-slate-900/40"
    >
      <form onSubmit={enviar} className="space-y-4 p-6" noValidate>
        <h2 id="resolver-titulo" className="text-lg font-semibold text-slate-900">
          Resolver la alerta #{numero}
        </h2>
        {esSalud && (
          <p className="text-sm text-slate-600">
            El reloj dejará de avisar a los contactos. Si fue una emergencia real, la alerta queda en seguimiento hasta
            que lo cierres.
          </p>
        )}

        {esSalud && (
          <fieldset>
            <legend className={CLASE_ETIQUETA}>¿Qué fue?</legend>
            <div className="mt-1 space-y-1">
              {(Object.keys(CLASIFICACION) as ClasificacionAlerta[]).map((opcion) => (
                <label key={opcion} className="flex items-center gap-2">
                  <input
                    type="radio"
                    name="clasificacion"
                    value={opcion}
                    checked={clasificacion === opcion}
                    onChange={() => setClasificacion(opcion)}
                  />
                  {CLASIFICACION[opcion]}
                </label>
              ))}
            </div>
          </fieldset>
        )}

        <label className="block">
          <span className={CLASE_ETIQUETA}>Observaciones</span>
          <textarea
            value={observaciones}
            onChange={(e) => setObservaciones(e.target.value)}
            rows={4}
            required
            autoFocus
            placeholder="Qué se hizo, con quién se habló, cómo está el usuario"
            className={CLASE_CAMPO}
          />
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
            disabled={guardando || !completo}
            className="rounded-md bg-sky-700 px-4 py-2 font-medium text-white hover:bg-sky-800 disabled:opacity-50"
          >
            {guardando ? 'Guardando…' : 'Resolver alerta'}
          </button>
        </div>
      </form>
    </dialog>
  );
}
