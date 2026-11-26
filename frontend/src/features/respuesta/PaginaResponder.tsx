import { useEffect, useState } from 'react';
import { ErrorApi } from '../../api/cliente';
import { useConfirmar } from '../../comun/Confirmacion';
import { Aviso, Cargando } from '../../comun/ui/Aviso';
import { claseBoton } from '../../comun/ui/Boton';
import { formatearFechaHora } from '../../comun/fechas';
import { CONDICION, InsigniaGravedad } from '../alertas/etiquetas';
import { PlantillaAcceso } from '../../navegacion/PlantillaAcceso';
import { respuestaApi } from './respuestaApi';
import { PasosOficina } from './PasosOficina';
import type { RespuestaContacto, ResultadoLlamadaContacto, VistaRespuesta } from './tipos';

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
 * confirmó que va avisa con "Llegué donde el paciente" al llegar: eso resuelve la alerta (24-11-2026). Después de
 * llamar, el contacto dice si le contestaron y recién entonces elige si va (26-11-2026).
 *
 * El enlace de un coordinador muestra, en cambio, los pasos de la oficina ({@link PasosOficina}): los mismos de los
 * mensajes de WhatsApp N1 (26-11-2026).
 */
export function PaginaResponder() {
  const [token] = useState(() => window.location.hash.slice(1));
  const [vista, setVista] = useState<VistaRespuesta | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState<RespuestaContacto | ResultadoLlamadaContacto | 'LLEGADA' | null>(null);
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

  /** Registra si le contestaron; recién entonces se muestran "Confirmo que voy" y "No puedo asistir". */
  async function registrarLlamada(resultado: ResultadoLlamadaContacto) {
    setEnviando(resultado);
    setError(null);
    try {
      setVista(await respuestaApi.resultadoLlamada(token, resultado));
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
    <PlantillaAcceso titulo="Aviso de la oficina PRLAC" subtitulo="Una persona de su red de cuidados necesita ayuda." ancho="max-w-[480px]">

        {!token ? (
          <Aviso tono="error">El enlace está incompleto. Ábralo tal como le llegó en el correo.</Aviso>
        ) : !vista ? (
          error ? (
            <Aviso tono="error">{error}</Aviso>
          ) : (
            <Cargando texto="Cargando la alerta…" />
          )
        ) : (
          <>
            <DatosAlerta vista={vista} />

            {vista.telefonoPaciente && <LlamarAlPaciente token={token} telefono={vista.telefonoPaciente} />}

            {vista.oficina ? (
              <PasosOficina vista={{ ...vista, oficina: vista.oficina }} token={token} alCambiar={setVista} />
            ) : vista.respuesta ? (
              <div className="space-y-3">
                <Respondida vista={vista} />
                {error && <Aviso tono="error">{error}</Aviso>}
                {vista.respuesta === 'VOY_EN_CAMINO' && !vista.llegada && !vista.cerrada && (
                  <button
                    type="button"
                    onClick={llegar}
                    disabled={enviando !== null}
                    className={`${claseBoton('principal')} w-full py-3 text-base`}
                  >
                    {enviando === 'LLEGADA' ? 'Enviando…' : 'Llegué donde el paciente'}
                  </button>
                )}
              </div>
            ) : vista.cerrada ? (
              <Aviso tono="info">El equipo ya atendió esta alerta. No es necesario que responda.</Aviso>
            ) : vista.telefonoPaciente && !vista.resultadoLlamada ? (
              <div className="space-y-3">
                <p className="text-sm text-apagado">Después de llamar, cuéntenos si le contestó:</p>
                {error && <Aviso tono="error">{error}</Aviso>}
                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() => registrarLlamada('CONTESTA')}
                    disabled={enviando !== null}
                    className={`${claseBoton('principal')} py-3 text-base`}
                  >
                    {enviando === 'CONTESTA' ? 'Enviando…' : 'Contestó'}
                  </button>
                  <button
                    type="button"
                    onClick={() => registrarLlamada('NO_CONTESTA')}
                    disabled={enviando !== null}
                    className={`${claseBoton('contorno')} py-3 text-base`}
                  >
                    {enviando === 'NO_CONTESTA' ? 'Enviando…' : 'No contestó'}
                  </button>
                </div>
              </div>
            ) : (
              <div className="space-y-3">
                {vista.resultadoLlamada && (
                  <p className="text-[13px] text-apagado">
                    {vista.resultadoLlamada === 'CONTESTA' ? 'Le contestó la llamada.' : 'No le contestó la llamada.'}
                  </p>
                )}
                <p className="text-sm text-apagado">Cuéntenos si puede ir a ver a la persona:</p>
                {error && <Aviso tono="error">{error}</Aviso>}
                <button
                  type="button"
                  onClick={() => responder('VOY_EN_CAMINO')}
                  disabled={enviando !== null}
                  className={`${claseBoton('principal')} w-full py-3 text-base`}
                >
                  {enviando === 'VOY_EN_CAMINO' ? 'Enviando…' : TEXTO.VOY_EN_CAMINO}
                </button>
                <button
                  type="button"
                  onClick={() => responder('NO_PUEDO')}
                  disabled={enviando !== null}
                  className={`${claseBoton('contorno')} w-full py-3 text-base`}
                >
                  {enviando === 'NO_PUEDO' ? 'Enviando…' : TEXTO.NO_PUEDO}
                </button>
              </div>
            )}
          </>
        )}
    </PlantillaAcceso>
  );
}

/** Quién, qué, gravedad y cuándo. En una grave, recuerda llamar al 131, como el correo. */
function DatosAlerta({ vista }: { vista: VistaRespuesta }) {
  return (
    <section className="space-y-2 rounded-panel border border-linea border-l-[4px] border-l-magenta bg-superficie p-4">
      <div className="flex items-center justify-between gap-3">
        <h2 className="font-semibold text-navy">
          {vista.alias ? `Alerta de ${vista.alias}` : `Alerta #${vista.numero}`}
        </h2>
        <InsigniaGravedad severidad={vista.severidad} />
      </div>
      <p className="text-texto">{CONDICION[vista.tipo]}</p>
      <p className="cifra text-[12.5px] text-apagado">
        {formatearFechaHora(vista.fechaHora)} · alerta #{vista.numero}
      </p>
      {vista.severidad === 'GRAVE' && !vista.cerrada && (
        <p className="rounded-[5px] bg-magenta-tinte px-3 py-2 text-[13px] text-magenta">
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
      className={`${claseBoton('normal')} w-full py-3 text-base`}
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
      className={`rounded-panel px-4 py-3 text-[13px] ${voy ? 'bg-verde-tinte text-verde-texto' : 'bg-superficie-sub text-texto'}`}
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
