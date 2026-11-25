import { useState } from 'react';
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
import { relojesApi } from '../relojes/relojesApi';
import { SemaforoReloj } from '../relojes/SemaforoReloj';
import { NOMBRE_ESTADO, type EstadoReloj } from '../relojes/tipos';
import { useSesion } from '../sesion/SesionContext';
import { ConfiguracionDelReloj } from './ConfiguracionDelReloj';
import { pacientesApi } from './pacientesApi';
import type { DetallePaciente } from './tipos';

/** Propiedades de RelojDelUsuario. */
interface Props {
  detalle: DetallePaciente;
  /** Vuelve a cargar la ficha después de un cambio */
  alCambiar: () => void;
}

/** Qué está haciendo el usuario en esta sección. */
type Modo = 'ver' | 'vincular' | 'reemplazar' | 'emparejar';

/** Texto de ayuda junto al campo del código. */
const AYUDA_CODIGO =
  'Aparece en la pantalla del reloj al abrir la app de AlertaCuida (ej. K7PM-42QX). Vence en 10 minutos.';

/**
 * El reloj del usuario: vincular uno si no tiene; si tiene, reemplazarlo, emparejarlo de nuevo o retirarlo.
 * Siempre con el código que muestra el reloj: el reloj ya tiene su token y nadie lo ve ni lo escribe.
 * Las reglas (consentimiento, reloj operativo y libre) las revisa el backend; aquí solo se ofrecen los
 * relojes que pueden servir.
 */
export function RelojDelUsuario({ detalle, alCambiar }: Props) {
  const { puede } = useSesion();
  const confirmar = useConfirmar();
  const { paciente, historial } = detalle;
  const vigente = historial.find((asignacion) => asignacion.fin === null) ?? null;
  const [modo, setModo] = useState<Modo>('ver');
  const [emparejado, setEmparejado] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [trabajando, setTrabajando] = useState(false);

  /**
   * Ejecuta una acción del backend y recarga la ficha.
   *
   * @param conCodigo si la acción usó un código de emparejamiento: entonces se avisa que el reloj quedó listo
   */
  async function hacer(accion: () => Promise<unknown>, conCodigo: boolean) {
    setTrabajando(true);
    setError(null);
    setEmparejado(false);
    try {
      await accion();
      setEmparejado(conCodigo);
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
      {emparejado && (
        <p role="status" className="rounded-md bg-verde-tinte px-3 py-2 text-sm text-verde-texto">
          Reloj emparejado. En unos segundos el reloj mostrará que quedó listo y descargará su configuración.
        </p>
      )}

      {vigente ? (
        <>
          <p className="text-texto">
            Usa el reloj <strong className="font-mono">{vigente.identificadorReloj}</strong> desde el{' '}
            {formatearFechaHora(vigente.inicio)}.
          </p>
          <EstadoDelReloj idDispositivo={vigente.idDispositivo} />
          <ConfiguracionDelReloj idPaciente={paciente.id} idAsignacion={vigente.idAsignacion} />
        </>
      ) : (
        <p className="text-apagado">No tiene reloj.</p>
      )}

      {editable && !paciente.datos.fechaConsentimiento && (
        <p className="rounded-md bg-naranja-tinte px-3 py-2 text-sm text-naranja-texto">
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
              <button onClick={() => setModo('emparejar')} className={CLASE_BOTON_SECUNDARIO}>
                Emparejar de nuevo
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
                  if (confirmado) hacer(() => pacientesApi.desvincular(paciente.id), false);
                }}
                className={CLASE_BOTON_SECUNDARIO}
              >
                Retirar reloj
              </button>
            </>
          )}
        </div>
      )}

      {editable && modo === 'emparejar' && (
        <EmparejarDeNuevo
          trabajando={trabajando}
          alCancelar={() => setModo('ver')}
          alEmparejar={(codigo) => hacer(() => pacientesApi.emparejar(paciente.id, codigo), true)}
        />
      )}

      {editable && (modo === 'vincular' || modo === 'reemplazar') && (
        <ElegirReloj
          reemplazo={modo === 'reemplazar'}
          trabajando={trabajando}
          alCancelar={() => setModo('ver')}
          alElegir={(idDispositivo, estadoAnterior, codigo) =>
            hacer(
              () =>
                modo === 'vincular'
                  ? pacientesApi.vincular(paciente.id, idDispositivo, codigo)
                  : pacientesApi.reemplazar(paciente.id, idDispositivo, estadoAnterior, codigo),
              true,
            )
          }
        />
      )}
    </div>
  );
}

/** Propiedades de EmparejarDeNuevo. */
interface PropsEmparejar {
  trabajando: boolean;
  alCancelar: () => void;
  alEmparejar: (codigo: string) => void;
}

/** Pide el código que muestra el reloj, para emparejarlo de nuevo (por ejemplo, tras restaurarlo). */
function EmparejarDeNuevo({ trabajando, alCancelar, alEmparejar }: PropsEmparejar) {
  const [codigo, setCodigo] = useState('');
  return (
    <div className="space-y-3 rounded-md border border-linea p-4">
      <p className="text-sm text-apagado">
        Úsalo si el reloj se restauró o perdió su vínculo: el token anterior deja de servir.
      </p>
      <CampoCodigo valor={codigo} alCambiar={setCodigo} />
      <div className="flex gap-2">
        <button
          disabled={!codigo.trim() || trabajando}
          onClick={() => alEmparejar(codigo.trim())}
          className={CLASE_BOTON_PRINCIPAL}
        >
          {trabajando ? 'Guardando…' : 'Emparejar'}
        </button>
        <button onClick={alCancelar} className={CLASE_BOTON_SECUNDARIO}>
          Cancelar
        </button>
      </div>
    </div>
  );
}

/** Campo para escribir el código de emparejamiento que muestra el reloj (obligatorio). */
function CampoCodigo({ valor, alCambiar }: { valor: string; alCambiar: (valor: string) => void }) {
  return (
    <label className="block">
      <span className={CLASE_ETIQUETA}>Código que muestra el reloj</span>
      <input
        value={valor}
        onChange={(e) => alCambiar(e.target.value.toUpperCase())}
        placeholder="K7PM-42QX"
        maxLength={20}
        autoComplete="off"
        spellCheck={false}
        className={`${CLASE_CAMPO} font-mono tracking-widest`}
      />
      <span className="mt-1 block text-xs text-apagado">{AYUDA_CODIGO}</span>
    </label>
  );
}

/** Propiedades de ElegirReloj. */
interface PropsElegir {
  /** true al reemplazar: también se pregunta en qué estado queda el reloj anterior */
  reemplazo: boolean;
  trabajando: boolean;
  alCancelar: () => void;
  /** codigo: el que muestra el reloj */
  alElegir: (idDispositivo: string, estadoAnterior: EstadoReloj | null, codigo: string) => void;
}

/** Lista de relojes operativos y libres para elegir uno, y el código que muestra el reloj elegido. */
function ElegirReloj({ reemplazo, trabajando, alCancelar, alElegir }: PropsElegir) {
  const { datos: relojes, error } = useDatos(relojesApi.listar);
  const libres = (relojes ?? []).filter((fila) => fila.reloj.datos.estado === 'OPERATIVO' && !fila.idPaciente);
  const [elegido, setElegido] = useState('');
  const [codigo, setCodigo] = useState('');
  const [estadoAnterior, setEstadoAnterior] = useState<EstadoReloj | ''>('EN_REPARACION');

  return (
    <div className="space-y-3 rounded-md border border-linea p-4">
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
      <CampoCodigo valor={codigo} alCambiar={setCodigo} />
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
          disabled={!elegido || !codigo.trim() || trabajando}
          onClick={() => alElegir(elegido, estadoAnterior || null, codigo.trim())}
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

/** Semáforo del reloj que usa el usuario, con su última sincronización y por qué no está verde. */
function EstadoDelReloj({ idDispositivo }: { idDispositivo: string }) {
  const { datos, error } = useDatos(relojesApi.estado, idDispositivo);
  if (error) return null;
  return (
    <div className="rounded-md border border-linea px-3 py-2">
      <SemaforoReloj estado={datos?.find((estado) => estado.idDispositivo === idDispositivo)} detalle />
    </div>
  );
}
