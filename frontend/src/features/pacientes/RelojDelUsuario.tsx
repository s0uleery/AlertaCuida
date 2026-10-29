import { useState } from 'react';
import { ErrorApi } from '../../api/cliente';
import { useConfirmar } from '../../comun/Confirmacion';
import { CLASE_BOTON_PRINCIPAL, CLASE_BOTON_SECUNDARIO, CLASE_CAMPO, CLASE_ERROR, CLASE_ETIQUETA } from '../../comun/estilos';
import { formatearFechaHora } from '../../comun/fechas';
import { useDatos } from '../../comun/useDatos';
import { relojesApi } from '../relojes/relojesApi';
import { NOMBRE_ESTADO, type EstadoReloj } from '../relojes/tipos';
import { useSesion } from '../sesion/SesionContext';
import { pacientesApi } from './pacientesApi';
import { TokenRelojMostrado } from './TokenRelojMostrado';
import type { DetallePaciente, TokenReloj } from './tipos';

/** Propiedades de RelojDelUsuario. */
interface Props {
  detalle: DetallePaciente;
  /** Vuelve a cargar la ficha después de un cambio */
  alCambiar: () => void;
}

/** Qué está haciendo el usuario en esta sección. */
type Modo = 'ver' | 'vincular' | 'reemplazar';

/**
 * El reloj del usuario: vincular uno si no tiene; si tiene, reemplazarlo, retirarlo o
 * generar otro token. Las reglas (consentimiento, reloj operativo y libre) las revisa el backend;
 * aquí solo se ofrecen los relojes que pueden servir.
 */
export function RelojDelUsuario({ detalle, alCambiar }: Props) {
  const { puede } = useSesion();
  const confirmar = useConfirmar();
  const { paciente, historial } = detalle;
  const vigente = historial.find((asignacion) => asignacion.fin === null) ?? null;
  const [modo, setModo] = useState<Modo>('ver');
  const [token, setToken] = useState<TokenReloj | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [trabajando, setTrabajando] = useState(false);

  /** Ejecuta una acción del backend, muestra el token si lo hay y recarga la ficha. */
  async function hacer(accion: () => Promise<TokenReloj | void>) {
    setTrabajando(true);
    setError(null);
    try {
      const resultado = await accion();
      if (resultado) setToken(resultado);
      setModo('ver');
      alCambiar();
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.');
    } finally {
      setTrabajando(false);
    }
  }

  const editable = puede('USUARIOS:EDITAR') && !paciente.fechaBaja;

  return (
    <div className="space-y-4">
      {token && <TokenRelojMostrado token={token} alCerrar={() => setToken(null)} />}

      {vigente ? (
        <p className="text-slate-700">
          Usa el reloj <strong className="font-mono">{vigente.identificadorReloj}</strong> desde el{' '}
          {formatearFechaHora(vigente.inicio)}.
        </p>
      ) : (
        <p className="text-slate-500">No tiene reloj.</p>
      )}

      {editable && !paciente.datos.fechaConsentimiento && (
        <p className="rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800">
          Para entregarle un reloj, primero registra la fecha del consentimiento informado.
        </p>
      )}

      {error && (
        <p role="alert" className={CLASE_ERROR}>
          {error}
        </p>
      )}

      {editable && modo === 'ver' && (
        <div className="flex flex-wrap gap-2">
          {!vigente && paciente.datos.fechaConsentimiento && (
            <button onClick={() => setModo('vincular')} className={CLASE_BOTON_PRINCIPAL}>
              Entregar reloj
            </button>
          )}
          {vigente && (
            <>
              <button onClick={() => setModo('reemplazar')} className={CLASE_BOTON_SECUNDARIO}>
                Reemplazar reloj
              </button>
              <button
                disabled={trabajando}
                onClick={() => hacer(() => pacientesApi.nuevoToken(paciente.id))}
                className={CLASE_BOTON_SECUNDARIO}
              >
                Generar otro token
              </button>
              <button
                disabled={trabajando}
                onClick={async () => {
                  const confirmado = await confirmar({
                    titulo: '¿Retirar el reloj?',
                    mensaje: 'Dejará de enviar datos y alertas de este usuario.',
                    textoConfirmar: 'Retirar reloj',
                    peligro: true,
                  });
                  if (confirmado) hacer(() => pacientesApi.desvincular(paciente.id));
                }}
                className={CLASE_BOTON_SECUNDARIO}
              >
                Retirar reloj
              </button>
            </>
          )}
        </div>
      )}

      {editable && modo !== 'ver' && (
        <ElegirReloj
          reemplazo={modo === 'reemplazar'}
          trabajando={trabajando}
          alCancelar={() => setModo('ver')}
          alElegir={(idDispositivo, estadoAnterior) =>
            hacer(() =>
              modo === 'vincular'
                ? pacientesApi.vincular(paciente.id, idDispositivo)
                : pacientesApi.reemplazar(paciente.id, idDispositivo, estadoAnterior),
            )
          }
        />
      )}
    </div>
  );
}

/** Propiedades de ElegirReloj. */
interface PropsElegir {
  /** true al reemplazar: también se pregunta en qué estado queda el reloj anterior */
  reemplazo: boolean;
  trabajando: boolean;
  alCancelar: () => void;
  alElegir: (idDispositivo: string, estadoAnterior: EstadoReloj | null) => void;
}

/** Lista de relojes operativos y libres para elegir uno. */
function ElegirReloj({ reemplazo, trabajando, alCancelar, alElegir }: PropsElegir) {
  const { datos: relojes, error } = useDatos(relojesApi.listar);
  const libres = (relojes ?? []).filter((fila) => fila.reloj.datos.estado === 'OPERATIVO' && !fila.idPaciente);
  const [elegido, setElegido] = useState('');
  const [estadoAnterior, setEstadoAnterior] = useState<EstadoReloj | ''>('EN_REPARACION');

  return (
    <div className="space-y-3 rounded-md border border-slate-200 p-4">
      {error && <p className={CLASE_ERROR}>{error}</p>}
      <label className="block">
        <span className={CLASE_ETIQUETA}>{reemplazo ? 'Reloj nuevo' : 'Reloj a entregar'}</span>
        <select value={elegido} onChange={(e) => setElegido(e.target.value)} className={CLASE_CAMPO}>
          <option value="">{libres.length ? 'Elige un reloj…' : 'No hay relojes operativos libres'}</option>
          {libres.map((fila) => (
            <option key={fila.reloj.id} value={fila.reloj.id}>
              {fila.reloj.datos.identificadorReloj}
              {fila.reloj.datos.modelo ? ` · ${fila.reloj.datos.modelo}` : ''}
            </option>
          ))}
        </select>
      </label>
      {reemplazo && (
        <label className="block">
          <span className={CLASE_ETIQUETA}>El reloj anterior queda</span>
          <select
            value={estadoAnterior}
            onChange={(e) => setEstadoAnterior(e.target.value as EstadoReloj | '')}
            className={CLASE_CAMPO}
          >
            <option value="">Sin cambio de estado</option>
            {Object.entries(NOMBRE_ESTADO).map(([valor, nombre]) => (
              <option key={valor} value={valor}>
                {nombre}
              </option>
            ))}
          </select>
        </label>
      )}
      <div className="flex gap-2">
        <button
          disabled={!elegido || trabajando}
          onClick={() => alElegir(elegido, estadoAnterior || null)}
          className={CLASE_BOTON_PRINCIPAL}
        >
          {trabajando ? 'Guardando…' : reemplazo ? 'Reemplazar' : 'Entregar'}
        </button>
        <button onClick={alCancelar} className={CLASE_BOTON_SECUNDARIO}>
          Cancelar
        </button>
      </div>
    </div>
  );
}
