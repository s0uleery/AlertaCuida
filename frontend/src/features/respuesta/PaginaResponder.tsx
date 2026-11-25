import { useEffect, useState } from 'react';
import { ErrorApi } from '../../api/cliente';
import { useConfirmar } from '../../comun/Confirmacion';
import { CLASE_BOTON_PRINCIPAL, CLASE_BOTON_SECUNDARIO, CLASE_ERROR, CLASE_TARJETA } from '../../comun/estilos';
import { formatearFechaHora } from '../../comun/fechas';
import { CONDICION, InsigniaGravedad } from '../alertas/etiquetas';
import { respuestaApi } from './respuestaApi';
import type { RespuestaContacto, VistaRespuesta } from './tipos';

/** Texto de cada botón, igual al del correo. */
const TEXTO: Record<RespuestaContacto, string> = {
  VOY_EN_CAMINO: 'Confirmo que voy',
  NO_PUEDO: 'No puedo asistir',
};

/**
 * Página pública /responder#<token>, que abre un contacto del paciente desde el enlace de su correo para
 * decir si va o no a ver a la persona. Abrirla no registra nada: la respuesta se envía al presionar un botón,
 * para que los revisores de enlaces del correo no respondan por la persona. Como en /activar, el token va
 * después de "#" y se borra de la barra de direcciones.
 *
 * Mientras la alerta está abierta, lo primero es "Llamar al paciente" (al número de la SIM del reloj), y quien
 * confirmó que va avisa con "Llegué donde el paciente" al llegar: eso resuelve la alerta (24-11-2026).
 */
export function PaginaResponder() {
  const [token] = useState(() => window.location.hash.slice(1));
  const [vista, setVista] = useState<VistaRespuesta | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState<RespuestaContacto | 'LLEGADA' | null>(null);
  const confirmar = useConfirmar();

  useEffect(() => {
    if (window.location.hash) {
      window.history.replaceState(null, '', window.location.pathname);
    }
  }, []);

  useEffect(() => {
    if (!token) return;
    respuestaApi
      .consultar(token)
      .then(setVista)
      .catch((e) => setError(mensaje(e)));
  }, [token]);

  /** Envía la respuesta elegida y muestra cómo quedó. */
  async function responder(respuesta: RespuestaContacto) {
    setEnviando(respuesta);
    setError(null);
    try {
      setVista(await respuestaApi.responder(token, respuesta));
    } catch (e) {
      setError(mensaje(e));
    } finally {
      setEnviando(null);
    }
  }

  /** Pide confirmación y avisa la llegada; la alerta queda resuelta. */
  async function llegar() {
    const confirmado = await confirmar({
      titulo: '¿Ya está con la persona?',
      mensaje: 'Esto avisa a la oficina que llegó y marca la alerta como atendida.',
      textoConfirmar: 'Sí, llegué',
    });
    if (!confirmado) return;
    setEnviando('LLEGADA');
    setError(null);
    try {
      setVista(await respuestaApi.llegar(token));
    } catch (e) {
      setError(mensaje(e));
    } finally {
      setEnviando(null);
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-100 px-4">
      <div className={`${CLASE_TARJETA} w-full max-w-md space-y-5 p-8`}>
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">AlertaCuida</h1>
          <p className="text-sm text-slate-500">Aviso de la oficina PRLAC</p>
        </div>

        {!token ? (
          <p role="alert" className={CLASE_ERROR}>
            El enlace está incompleto. Ábralo tal como le llegó en el correo.
          </p>
        ) : !vista ? (
          error ? (
            <p role="alert" className={CLASE_ERROR}>
              {error}
            </p>
          ) : (
            <p className="text-sm text-slate-500">Cargando la alerta…</p>
          )
        ) : (
          <>
            <DatosAlerta vista={vista} />

            {vista.telefonoPaciente && <LlamarAlPaciente token={token} telefono={vista.telefonoPaciente} />}

            {vista.respuesta ? (
              <div className="space-y-3">
                <Respondida vista={vista} />
                {error && (
                  <p role="alert" className={CLASE_ERROR}>
                    {error}
                  </p>
                )}
                {vista.respuesta === 'VOY_EN_CAMINO' && !vista.llegada && !vista.cerrada && (
                  <button
                    type="button"
                    onClick={llegar}
                    disabled={enviando !== null}
                    className={`${CLASE_BOTON_PRINCIPAL} w-full py-3 text-lg`}
                  >
                    {enviando === 'LLEGADA' ? 'Enviando…' : 'Llegué donde el paciente'}
                  </button>
                )}
              </div>
            ) : vista.cerrada ? (
              <p className="rounded-md bg-slate-50 px-4 py-3 text-slate-700">
                El equipo ya atendió esta alerta. No es necesario que responda.
              </p>
            ) : (
              <div className="space-y-3">
                <p className="text-sm text-slate-600">Cuéntenos si puede ir a ver a la persona:</p>
                {error && (
                  <p role="alert" className={CLASE_ERROR}>
                    {error}
                  </p>
                )}
                <button
                  type="button"
                  onClick={() => responder('VOY_EN_CAMINO')}
                  disabled={enviando !== null}
                  className={`${CLASE_BOTON_PRINCIPAL} w-full py-3 text-lg`}
                >
                  {enviando === 'VOY_EN_CAMINO' ? 'Enviando…' : TEXTO.VOY_EN_CAMINO}
                </button>
                <button
                  type="button"
                  onClick={() => responder('NO_PUEDO')}
                  disabled={enviando !== null}
                  className={`${CLASE_BOTON_SECUNDARIO} w-full py-3 text-lg`}
                >
                  {enviando === 'NO_PUEDO' ? 'Enviando…' : TEXTO.NO_PUEDO}
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </main>
  );
}

/** Quién, qué, gravedad y cuándo. En una grave, recuerda llamar al 131, como el correo. */
function DatosAlerta({ vista }: { vista: VistaRespuesta }) {
  return (
    <section className="space-y-2 rounded-md border border-slate-200 p-4">
      <div className="flex items-center justify-between gap-3">
        <h2 className="font-semibold text-slate-900">
          {vista.alias ? `Alerta de ${vista.alias}` : `Alerta #${vista.numero}`}
        </h2>
        <InsigniaGravedad severidad={vista.severidad} />
      </div>
      <p className="text-slate-700">{CONDICION[vista.tipo]}</p>
      <p className="text-sm text-slate-500">
        {formatearFechaHora(vista.fechaHora)} · alerta #{vista.numero}
      </p>
      {vista.severidad === 'GRAVE' && !vista.cerrada && (
        <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-800">
          Es una alerta grave: si no logra comunicarse con la persona, llame al SAMU{' '}
          <a href="tel:131" className="font-semibold underline">
            131
          </a>
          .
        </p>
      )}
    </section>
  );
}

/**
 * Abre el marcador del teléfono con el número del reloj del paciente. Al presionarlo deja registrado el clic,
 * sin esperar la respuesta: si eso falla, la llamada sale igual.
 */
function LlamarAlPaciente({ token, telefono }: { token: string; telefono: string }) {
  return (
    <a
      href={`tel:${telefono}`}
      onClick={() => {
        respuestaApi.llamar(token).catch(() => {
          // No impide llamar: solo no queda registrado el intento
        });
      }}
      className={`${CLASE_BOTON_SECUNDARIO} block w-full py-3 text-center text-lg`}
    >
      Llamar al paciente
    </a>
  );
}

/** Lo que ya respondió: vale solo la primera respuesta. */
function Respondida({ vista }: { vista: VistaRespuesta }) {
  const voy = vista.respuesta === 'VOY_EN_CAMINO';
  return (
    <div
      className={`rounded-md px-4 py-3 ${voy ? 'bg-emerald-50 text-emerald-900' : 'bg-slate-50 text-slate-800'}`}
    >
      <p className="font-medium">Su respuesta: {TEXTO[vista.respuesta!]}</p>
      {vista.fechaRespuesta && (
        <p className="text-sm">Registrada el {formatearFechaHora(vista.fechaRespuesta)}.</p>
      )}
      <p className="mt-2 text-sm">
        {vista.llegada
          ? `Gracias. Se registró su llegada el ${formatearFechaHora(vista.llegada)} y la alerta quedó atendida.`
          : voy
            ? 'Gracias. Quedó registrado que va en camino: no avisaremos a los demás contactos. Al llegar, avísenos con el botón de abajo.'
            : 'Gracias por avisar. Seguiremos avisando a los demás contactos.'}
      </p>
    </div>
  );
}

/** @returns el mensaje del backend, o uno genérico si no hubo conexión */
function mensaje(e: unknown): string {
  return e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor. Intente de nuevo.';
}
