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
import { useDatos } from '../../comun/useDatos';
import { useSesion } from '../sesion/SesionContext';
import { pacientesApi } from './pacientesApi';
import type { AjustesClinicos, Modulos, PerfilClinico, Umbral, UmbralVigente, VariableUmbral } from './tipos';

/** Nombre de cada módulo, en el orden en que se muestran. */
const MODULOS: [keyof Modulos, string][] = [
  ['caidas', 'Caídas'],
  ['fc', 'Frecuencia cardíaca'],
  ['spo2', 'Saturación de oxígeno'],
  ['inactividad', 'Inactividad prolongada'],
  ['tendencia', 'Tendencia anormal'],
];

/** Propiedades de PerfilClinicoDelUsuario. */
interface Props {
  idPaciente: string;
}

/**
 * Perfil clínico del usuario: qué vigila el reloj, opciones y umbrales de FC y SpO2. Donde no hay
 * ajustes propios rigen los valores base del sistema. Cada cambio queda en el historial con quién y
 * cuándo, y el reloj lo recibe en su configuración.
 */
export function PerfilClinicoDelUsuario({ idPaciente }: Props) {
  const { puede } = useSesion();
  const { datos, error, recargar } = useDatos(() => pacientesApi.perfilClinico(idPaciente), idPaciente);
  const [errorCambio, setErrorCambio] = useState<string | null>(null);
  const editable = puede('USUARIOS:EDITAR');

  /** Ejecuta un cambio en el backend y recarga el perfil; devuelve si salió bien. */
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
    <div className="space-y-6">
      {errorCambio && (
        <p role="alert" className={CLASE_ERROR}>
          {errorCambio}
        </p>
      )}
      <FormularioAjustes
        // Vuelve a armar el formulario cuando cambia lo guardado
        key={datos.ajustesDesde ?? 'base'}
        perfil={datos}
        editable={editable}
        alGuardar={(ajustes) => cambiar(() => pacientesApi.cambiarAjustes(idPaciente, ajustes))}
      />
      <div className="grid gap-4 sm:grid-cols-2">
        {(['FC', 'SPO2'] as VariableUmbral[]).map((variable) => (
          <TarjetaUmbral
            key={`${variable}-${(variable === 'FC' ? datos.fc : datos.spo2).desde ?? 'base'}`}
            variable={variable}
            vigente={variable === 'FC' ? datos.fc : datos.spo2}
            editable={editable}
            alAjustar={(umbral) => cambiar(() => pacientesApi.ajustarUmbral(idPaciente, variable, umbral))}
            alVolverAlBase={() => cambiar(() => pacientesApi.volverAlUmbralBase(idPaciente, variable))}
          />
        ))}
      </div>
      <p className="text-xs text-slate-500">
        Los valores base son provisionales hasta que los valide el equipo clínico.
      </p>
    </div>
  );
}

/** Propiedades de FormularioAjustes. */
interface PropsAjustes {
  perfil: PerfilClinico;
  editable: boolean;
  alGuardar: (ajustes: AjustesClinicos) => Promise<boolean>;
}

/** Módulos, GPS, modo conservador y minutos de espera, con un solo botón para guardar. */
function FormularioAjustes({ perfil, editable, alGuardar }: PropsAjustes) {
  const [ajustes, setAjustes] = useState<AjustesClinicos>({ ...perfil.ajustes, motivo: null });
  const [guardando, setGuardando] = useState(false);

  /** Envía los ajustes; el backend no crea otra versión si nada cambió. */
  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    setGuardando(true);
    await alGuardar(ajustes);
    setGuardando(false);
  }

  return (
    <form onSubmit={enviar} className="space-y-4">
      <fieldset disabled={!editable} className="space-y-4">
        <div>
          <legend className={CLASE_ETIQUETA}>Qué vigila el reloj</legend>
          <div className="mt-2 grid gap-2 sm:grid-cols-3">
            {MODULOS.map(([clave, nombre]) => (
              <label key={clave} className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={ajustes.modulos[clave]}
                  onChange={(e) => setAjustes({ ...ajustes, modulos: { ...ajustes.modulos, [clave]: e.target.checked } })}
                />
                {nombre}
              </label>
            ))}
          </div>
        </div>
        <div className="grid gap-3 sm:grid-cols-3">
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={ajustes.gpsActivo}
              onChange={(e) => setAjustes({ ...ajustes, gpsActivo: e.target.checked })}
            />
            GPS (solo para ubicar; no genera alertas)
          </label>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={ajustes.modoConservador}
              onChange={(e) => setAjustes({ ...ajustes, modoConservador: e.target.checked })}
            />
            Modo conservador (alerta antes)
          </label>
          <label className="block text-sm">
            <span className={CLASE_ETIQUETA}>Minutos de espera entre contactos</span>
            <input
              type="number"
              min={1}
              max={60}
              placeholder={`Valor base (rige ${perfil.minutosEspera})`}
              value={ajustes.minutosEspera ?? ''}
              onChange={(e) =>
                setAjustes({ ...ajustes, minutosEspera: e.target.value === '' ? null : Number(e.target.value) })
              }
              className={CLASE_CAMPO}
            />
          </label>
        </div>
        {editable && (
          <label className="block text-sm">
            <span className={CLASE_ETIQUETA}>Motivo del cambio (opcional)</span>
            <input
              maxLength={500}
              value={ajustes.motivo ?? ''}
              onChange={(e) => setAjustes({ ...ajustes, motivo: e.target.value || null })}
              className={CLASE_CAMPO}
            />
          </label>
        )}
      </fieldset>
      <div className="flex flex-wrap items-center gap-3">
        {editable && (
          <button type="submit" disabled={guardando} className={CLASE_BOTON_PRINCIPAL}>
            {guardando ? 'Guardando…' : 'Guardar ajustes'}
          </button>
        )}
        <span className="text-sm text-slate-500">
          {perfil.ajustesDesde
            ? `Último cambio: ${formatearFechaHora(perfil.ajustesDesde)}${perfil.ajustes.motivo ? ` (${perfil.ajustes.motivo})` : ''}`
            : 'Sin cambios: rigen los valores por defecto.'}
        </span>
      </div>
    </form>
  );
}

/** Propiedades de TarjetaUmbral. */
interface PropsUmbral {
  variable: VariableUmbral;
  vigente: UmbralVigente;
  editable: boolean;
  alAjustar: (umbral: Umbral) => Promise<boolean>;
  alVolverAlBase: () => Promise<boolean>;
}

/** Umbral de una variable: lo que rige y, con permiso, el formulario para ajustarlo. */
function TarjetaUmbral({ variable, vigente, editable, alAjustar, alVolverAlBase }: PropsUmbral) {
  const conAltos = variable === 'FC';
  const unidad = conAltos ? 'lpm' : '%';
  const confirmar = useConfirmar();
  const [editando, setEditando] = useState(false);
  const [valores, setValores] = useState(vigente.umbral);
  const { graveBajo, normalMin, normalMax, graveAlto } = vigente.umbral;

  /** Envía los límites escritos. */
  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (await alAjustar(conAltos ? valores : { ...valores, normalMax: null, graveAlto: null })) setEditando(false);
  }

  /** Campo numérico para un límite. */
  function limite(clave: keyof Umbral, etiqueta: string) {
    return (
      <label className="block text-sm">
        <span className={CLASE_ETIQUETA}>{etiqueta}</span>
        <input
          type="number"
          required
          step="any"
          value={valores[clave] ?? ''}
          onChange={(e) => setValores({ ...valores, [clave]: e.target.value === '' ? null : Number(e.target.value) })}
          className={CLASE_CAMPO}
        />
      </label>
    );
  }

  return (
    <div className="rounded-lg border border-slate-200 p-4">
      <div className="mb-2 flex items-center justify-between">
        <h3 className="font-medium text-slate-800">{conAltos ? 'Frecuencia cardíaca' : 'Saturación de oxígeno'}</h3>
        <span className={`text-xs ${vigente.propio ? 'text-sky-700' : 'text-slate-500'}`}>
          {vigente.propio ? 'Ajustado para este usuario' : 'Valor base'}
        </span>
      </div>
      {editando ? (
        <form onSubmit={enviar} className="space-y-3">
          <div className="grid grid-cols-2 gap-2">
            {limite('graveBajo', 'Grave bajo')}
            {limite('normalMin', 'Mínimo normal')}
            {conAltos && limite('normalMax', 'Máximo normal')}
            {conAltos && limite('graveAlto', 'Grave alto')}
          </div>
          <div className="flex gap-2">
            <button type="submit" className={CLASE_BOTON_PRINCIPAL}>
              Guardar
            </button>
            <button type="button" onClick={() => setEditando(false)} className={CLASE_BOTON_SECUNDARIO}>
              Cancelar
            </button>
          </div>
        </form>
      ) : (
        <>
          <p className="text-sm text-slate-700">
            Normal: {normalMin}
            {conAltos ? ` a ${normalMax}` : ' o más'} {unidad}
          </p>
          <p className="text-sm text-slate-700">
            Grave: bajo {graveBajo}
            {conAltos && ` o sobre ${graveAlto}`} {unidad}
          </p>
          <p className="text-xs text-slate-500">Entre lo normal y lo grave, la lectura es leve.</p>
          {vigente.propio && vigente.desde && (
            <p className="text-xs text-slate-500">Desde {formatearFechaHora(vigente.desde)}</p>
          )}
          {editable && (
            <div className="mt-3 flex gap-3 text-sm">
              <button onClick={() => setEditando(true)} className="text-sky-700 hover:underline">
                Ajustar
              </button>
              {vigente.propio && (
                <button
                  onClick={async () => {
                    const confirmado = await confirmar({
                      titulo: '¿Volver al valor base?',
                      mensaje: 'El ajuste queda en el historial.',
                      textoConfirmar: 'Volver al valor base',
                      peligro: true,
                    });
                    if (confirmado) alVolverAlBase();
                  }}
                  className="text-red-700 hover:underline"
                >
                  Volver al valor base
                </button>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
}
