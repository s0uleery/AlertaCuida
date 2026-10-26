import { useState, type FormEvent } from 'react';
import { ErrorApi } from '../../api/cliente';
import { CLASE_BOTON_SECUNDARIO, CLASE_CAMPO, CLASE_ERROR } from '../../comun/estilos';
import { useDatos } from '../../comun/useDatos';
import { useSesion } from '../sesion/SesionContext';
import { pacientesApi } from './pacientesApi';
import type { Condicion, TipoCondicion } from './tipos';

/** Título, texto de ayuda y sugerencias de cada lista. Se puede escribir cualquier otra. */
const GRUPOS: Record<TipoCondicion, { titulo: string; vacio: string; sugerencias: string[] }> = {
  ENFERMEDAD: {
    titulo: 'Enfermedades',
    vacio: 'Sin enfermedades registradas.',
    sugerencias: [
      'Hipertensión arterial',
      'Diabetes mellitus tipo 2',
      'EPOC',
      'Insuficiencia cardíaca',
      'Arritmia',
      'Enfermedad renal crónica',
      'Artrosis',
      'Osteoporosis',
      'Alzheimer',
      'Parkinson',
      'Secuela de accidente cerebrovascular',
      'Depresión',
    ],
  },
  SITUACION: {
    titulo: 'Situaciones',
    vacio: 'Sin situaciones registradas.',
    sugerencias: [
      'Vive sola',
      'Vive solo',
      'Movilidad reducida',
      'Usa bastón o andador',
      'Usa silla de ruedas',
      'Riesgo de caídas',
      'Discapacidad visual',
      'Discapacidad auditiva',
      'Postrada',
      'Postrado',
    ],
  },
};

/** Propiedades de CondicionesDelUsuario. */
interface Props {
  idPaciente: string;
}

/**
 * Enfermedades y situaciones del usuario, en dos listas. Con USUARIOS:EDITAR se pueden agregar
 * (texto libre con sugerencias) y quitar. Las repetidas las rechaza el backend.
 */
export function CondicionesDelUsuario({ idPaciente }: Props) {
  const { puede } = useSesion();
  const { datos, error, recargar } = useDatos(() => pacientesApi.condiciones(idPaciente), idPaciente);
  const [errorCambio, setErrorCambio] = useState<string | null>(null);
  const editable = puede('USUARIOS:EDITAR');

  /** Ejecuta un cambio en el backend y recarga las listas; devuelve si salió bien. */
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
  if (!datos) return <p className="text-slate-500">Cargando…</p>;

  return (
    <div className="space-y-4">
      {errorCambio && (
        <p role="alert" className={CLASE_ERROR}>
          {errorCambio}
        </p>
      )}
      <div className="grid gap-6 sm:grid-cols-2">
        {(Object.keys(GRUPOS) as TipoCondicion[]).map((tipo) => (
          <ListaCondiciones
            key={tipo}
            tipo={tipo}
            condiciones={datos.filter((condicion) => condicion.tipo === tipo)}
            editable={editable}
            alAgregar={(descripcion) => cambiar(() => pacientesApi.agregarCondicion(idPaciente, tipo, descripcion))}
            alQuitar={(condicion) => {
              if (window.confirm(`¿Quitar "${condicion.descripcion}"?`)) {
                cambiar(() => pacientesApi.quitarCondicion(idPaciente, condicion.id));
              }
            }}
          />
        ))}
      </div>
    </div>
  );
}

/** Propiedades de ListaCondiciones. */
interface PropsLista {
  tipo: TipoCondicion;
  /** Solo las de este tipo, ya ordenadas por el backend */
  condiciones: Condicion[];
  editable: boolean;
  /** Agrega la descripción escrita; devuelve si salió bien (para limpiar el campo) */
  alAgregar: (descripcion: string) => Promise<boolean>;
  alQuitar: (condicion: Condicion) => void;
}

/** Una de las dos listas, con el campo para agregar al final. */
function ListaCondiciones({ tipo, condiciones, editable, alAgregar, alQuitar }: PropsLista) {
  const grupo = GRUPOS[tipo];
  const [texto, setTexto] = useState('');
  const [guardando, setGuardando] = useState(false);
  const idSugerencias = `sugerencias-${tipo}`;
  // No sugerir lo que ya tiene
  const registradas = new Set(condiciones.map((condicion) => condicion.descripcion.toLowerCase()));
  const sugerencias = grupo.sugerencias.filter((sugerencia) => !registradas.has(sugerencia.toLowerCase()));

  /** Envía lo escrito y, si se guardó, limpia el campo. */
  async function agregar(evento: FormEvent) {
    evento.preventDefault();
    if (!texto.trim()) return;
    setGuardando(true);
    if (await alAgregar(texto)) setTexto('');
    setGuardando(false);
  }

  return (
    <div>
      <h3 className="mb-2 font-medium text-slate-800">{grupo.titulo}</h3>
      {condiciones.length === 0 ? (
        <p className="text-sm text-slate-500">{grupo.vacio}</p>
      ) : (
        <ul className="divide-y divide-slate-100">
          {condiciones.map((condicion) => (
            <li key={condicion.id} className="flex items-center justify-between gap-2 py-1.5">
              <span className="text-slate-800">{condicion.descripcion}</span>
              {editable && (
                <button
                  onClick={() => alQuitar(condicion)}
                  className="text-sm text-red-700 hover:underline"
                  aria-label={`Quitar ${condicion.descripcion}`}
                >
                  Quitar
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      {editable && (
        <form onSubmit={agregar} className="mt-3 flex gap-2">
          <input
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
            list={idSugerencias}
            maxLength={200}
            placeholder={`Agregar ${tipo === 'ENFERMEDAD' ? 'enfermedad' : 'situación'}…`}
            aria-label={`Agregar ${tipo === 'ENFERMEDAD' ? 'enfermedad' : 'situación'}`}
            className={`${CLASE_CAMPO} mt-0`}
          />
          <datalist id={idSugerencias}>
            {sugerencias.map((sugerencia) => (
              <option key={sugerencia} value={sugerencia} />
            ))}
          </datalist>
          <button type="submit" disabled={!texto.trim() || guardando} className={CLASE_BOTON_SECUNDARIO}>
            {guardando ? 'Guardando…' : 'Agregar'}
          </button>
        </form>
      )}
    </div>
  );
}
