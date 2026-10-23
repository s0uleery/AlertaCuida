import { useState, type FormEvent } from 'react';
import { ErrorApi } from '../../api/cliente';
import {
  CLASE_BOTON_PRINCIPAL,
  CLASE_BOTON_SECUNDARIO,
  CLASE_CAMPO,
  CLASE_ERROR,
  CLASE_ETIQUETA,
} from '../../comun/estilos';
import { NOMBRE_ESTADO, type DatosReloj, type EstadoReloj } from './tipos';

const VACIO: DatosReloj = {
  identificadorReloj: '',
  modelo: null,
  iccid: null,
  numeroSim: null,
  fechaVencimientoPlan: null,
  estado: 'OPERATIVO',
};

interface Props {
  /** Datos actuales al editar; sin ellos, el formulario parte vacío (registro) */
  inicial?: DatosReloj;
  /** Guarda en el backend; si falla, el formulario muestra el mensaje */
  alGuardar: (datos: DatosReloj) => Promise<void>;
  alCancelar: () => void;
}

/** Formulario de registro y edición de un reloj del inventario. */
export function FormularioReloj({ inicial, alGuardar, alCancelar }: Props) {
  const [datos, setDatos] = useState<DatosReloj>(inicial ?? VACIO);
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  /** Actualiza un campo; un texto vacío se guarda como null. */
  function cambiar<K extends keyof DatosReloj>(campo: K, valor: DatosReloj[K]) {
    setDatos((anteriores) => ({ ...anteriores, [campo]: valor === '' ? null : valor }));
  }

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    setEnviando(true);
    setError(null);
    try {
      await alGuardar(datos);
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form onSubmit={enviar} className="grid gap-4 sm:grid-cols-3" noValidate>
      <label className="block">
        <span className={CLASE_ETIQUETA}>Identificador del reloj</span>
        <input
          value={datos.identificadorReloj}
          onChange={(e) => setDatos({ ...datos, identificadorReloj: e.target.value })}
          required
          className={CLASE_CAMPO}
        />
      </label>
      <label className="block">
        <span className={CLASE_ETIQUETA}>Modelo</span>
        <input
          value={datos.modelo ?? ''}
          onChange={(e) => cambiar('modelo', e.target.value)}
          placeholder="Galaxy Watch"
          className={CLASE_CAMPO}
        />
      </label>
      <label className="block">
        <span className={CLASE_ETIQUETA}>Estado</span>
        <select
          value={datos.estado}
          onChange={(e) => cambiar('estado', e.target.value as EstadoReloj)}
          className={CLASE_CAMPO}
        >
          {Object.entries(NOMBRE_ESTADO).map(([valor, nombre]) => (
            <option key={valor} value={valor}>
              {nombre}
            </option>
          ))}
        </select>
      </label>
      <label className="block">
        <span className={CLASE_ETIQUETA}>ICCID de la SIM</span>
        <input value={datos.iccid ?? ''} onChange={(e) => cambiar('iccid', e.target.value)} className={CLASE_CAMPO} />
      </label>
      <label className="block">
        <span className={CLASE_ETIQUETA}>Número de la SIM</span>
        <input
          value={datos.numeroSim ?? ''}
          onChange={(e) => cambiar('numeroSim', e.target.value)}
          placeholder="+569…"
          className={CLASE_CAMPO}
        />
      </label>
      <label className="block">
        <span className={CLASE_ETIQUETA}>Vencimiento del plan de datos</span>
        <input
          type="date"
          value={datos.fechaVencimientoPlan ?? ''}
          onChange={(e) => cambiar('fechaVencimientoPlan', e.target.value)}
          className={CLASE_CAMPO}
        />
      </label>

      {error && (
        <p role="alert" className={`${CLASE_ERROR} sm:col-span-3`}>
          {error}
        </p>
      )}

      <div className="flex gap-2 sm:col-span-3">
        <button type="submit" disabled={enviando || !datos.identificadorReloj.trim()} className={CLASE_BOTON_PRINCIPAL}>
          {enviando ? 'Guardando…' : 'Guardar'}
        </button>
        <button type="button" onClick={alCancelar} className={CLASE_BOTON_SECUNDARIO}>
          Cancelar
        </button>
      </div>
    </form>
  );
}
