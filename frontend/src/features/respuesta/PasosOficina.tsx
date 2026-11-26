import { useState, type ReactNode } from 'react';
import { ErrorApi } from '../../api/cliente';
import { useConfirmar } from '../../comun/Confirmacion';
import { formatearFechaHora } from '../../comun/fechas';
import { Aviso } from '../../comun/ui/Aviso';
import { claseBoton, type VarianteBoton } from '../../comun/ui/Boton';
import { respuestaApi } from './respuestaApi';
import type { PasoOficina, VistaOficina, VistaRespuesta } from './tipos';

/** Un botón de un paso: qué paso da, su texto, su variante y, si cierra la alerta, la pregunta de confirmación. */
interface OpcionPaso {
  paso: PasoOficina;
  texto: string;
  variante?: VarianteBoton;
  /** Si cierra la alerta, se pide confirmar antes con este texto. */
  cierra?: string;
}

/**
 * Pasos de la oficina PRLAC en la página del enlace de un coordinador: los mismos de los mensajes de WhatsApp
 * N1-02 a N1-11 (26-11-2026). Muestra una pantalla por etapa; la etapa la calcula el backend con lo registrado,
 * así que al volver a abrir el enlace (o el del recordatorio) sigue donde quedó.
 *
 * @param vista    lo que devolvió el backend, con `oficina`
 * @param token    el del enlace
 * @param alCambiar recibe la vista nueva después de cada paso
 */
export function PasosOficina({
  vista,
  token,
  alCambiar,
}: {
  vista: VistaRespuesta & { oficina: VistaOficina };
  token: string;
  alCambiar: (vista: VistaRespuesta) => void;
}) {
  const { oficina } = vista;
  const [enviando, setEnviando] = useState<PasoOficina | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [comentario, setComentario] = useState('');
  const [comentado, setComentado] = useState(false);
  const confirmar = useConfirmar();

  /** Da el paso (con confirmación si cierra la alerta) y muestra cómo quedó; si falla, recarga la vista. */
  async function dar(opcion: OpcionPaso, texto?: string) {
    if (opcion.cierra) {
      const confirmado = await confirmar({
        titulo: '¿Cerrar la alerta?',
        mensaje: opcion.cierra,
        textoConfirmar: 'Sí, cerrar',
      });
      if (!confirmado) return;
    }
    setEnviando(opcion.paso);
    setError(null);
    try {
      alCambiar(await respuestaApi.paso(token, opcion.paso, texto));
      if (opcion.paso === 'COMENTARIO') {
        setComentario('');
        setComentado(true);
      }
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor. Intente de nuevo.');
      // Si otra persona avanzó, la página debe mostrar dónde quedó
      respuestaApi.consultar(token).then(alCambiar).catch(() => {});
    } finally {
      setEnviando(null);
    }
  }

  /** @returns los botones de un paso, uno debajo del otro */
  function botones(opciones: OpcionPaso[]) {
    return (
      <div className="space-y-2">
        {opciones.map((opcion, i) => (
          <button
            key={opcion.paso}
            type="button"
            onClick={() => dar(opcion)}
            disabled={enviando !== null}
            className={`${claseBoton(opcion.variante ?? (i === 0 ? 'principal' : 'contorno'))} w-full py-3 text-base`}
          >
            {enviando === opcion.paso ? 'Enviando…' : opcion.texto}
          </button>
        ))}
      </div>
    );
  }

  const enlaceFicha = (
    <a href={`/alertas/${oficina.idAlerta}`} className="font-semibold text-teal-oscuro underline">
      ficha de la alerta
    </a>
  );

  return (
    <div className="space-y-3">
      {error && <Aviso tono="error">{error}</Aviso>}
      {pantalla()}
    </div>
  );

  /** @returns la pantalla de la etapa actual */
  function pantalla(): ReactNode {
    switch (oficina.etapa) {
      case 'SIN_TOMAR':
        return (
          <>
            <Texto>Nadie de la oficina ha tomado esta alerta. Si usted la atiende, tómela para quedar a cargo.</Texto>
            {botones([{ paso: 'TOMAR', texto: 'Tomo la alerta' }])}
          </>
        );
      case 'A_CARGO_DE_OTRO':
        return <Aviso tono="info">La alerta está a cargo de {oficina.responsable}.</Aviso>;
      case 'LLAMAR':
        return (
          <>
            <Texto>
              <b>Usted quedó a cargo de esta alerta.</b>
              <br />
              1. Llame a la persona al reloj{vista.telefonoPaciente ? `: ${vista.telefonoPaciente}` : ''}.
              <br />
              2. Indique aquí el resultado de la llamada:
            </Texto>
            {botones([
              { paso: 'PACIENTE_BIEN', texto: 'Paciente está bien' },
              { paso: 'NECESITA_ATENCION', texto: 'Necesita atención' },
              { paso: 'NO_CONTESTA', texto: 'No contesta' },
            ])}
          </>
        );
      case 'CONFIRMAR_EVENTO':
        return (
          <>
            <Texto>
              Se registró que la persona se encuentra bien.
              <br />
              Para cerrar la alerta, indique si el evento detectado ocurrió:
            </Texto>
            {botones([
              { paso: 'OCURRIO', texto: 'Sí ocurrió', cierra: 'La alerta se cierra como positiva y queda en seguimiento.' },
              { paso: 'FALSA_ALARMA', texto: 'Falsa alarma', cierra: 'La alerta se cierra como falsa alarma.' },
            ])}
          </>
        );
      case 'COORDINAR_VISITA':
        return (
          <>
            <Texto>
              {oficina.resultadoLlamada === 'NO_CONTESTA'
                ? 'La persona no contestó la llamada.'
                : 'Se registró que la persona necesita atención.'}
              <br />
              Coordine con el personal del programa para que acuda al domicilio. En caso de que no haya personal
              disponible, seleccione la opción correspondiente.
            </Texto>
            {botones([
              { paso: 'PERSONAL_ASIGNADO', texto: 'Personal asignado' },
              { paso: 'SIN_DISPONIBILIDAD', texto: 'Sin disponibilidad', variante: 'peligro' },
            ])}
          </>
        );
      case 'SIN_DISPONIBILIDAD':
        return (
          <Aviso tono="advertencia">
            No fue posible atender la alerta desde el PRLAC.{' '}
            {oficina.ultimoContacto && oficina.avisoUltimoContacto
              ? `Se avisó a ${oficina.ultimoContacto} el ${formatearFechaHora(oficina.avisoUltimoContacto)}, y el reloj sigue avisando a los contactos del usuario.`
              : 'El reloj sigue avisando a los contactos del usuario.'}
            <br />
            Usted sigue a cargo del seguimiento; la respuesta quedará registrada en la {enlaceFicha}.
          </Aviso>
        );
      case 'ESPERANDO_LLEGADA':
        return oficina.recordatorio ? (
          <>
            <Texto>Aún no se registra la llegada del personal. ¿Sigue en camino?</Texto>
            {botones([
              { paso: 'LLEGO', texto: 'Ya llegó' },
              { paso: 'SIGUE_EN_CAMINO', texto: 'Sigue en camino' },
            ])}
          </>
        ) : (
          <>
            <Texto>
              <b>Personal asignado.</b>
              <br />
              El personal tiene la responsabilidad de avisarle cuando llegue al domicilio. Una vez se lo comunique,
              regístrelo acá:
            </Texto>
            {botones([{ paso: 'LLEGO', texto: 'Ya llegó' }])}
          </>
        );
      case 'RESULTADO_VISITA':
        return (
          <>
            <Texto>
              Se registró que la persona está acompañada desde el{' '}
              {oficina.llegada ? formatearFechaHora(oficina.llegada) : ''}.
              <br />
              Cuando el personal le informe cómo la encontró, indique el resultado:
            </Texto>
            {botones([
              { paso: 'VISITA_BIEN', texto: 'Persona bien' },
              {
                paso: 'APOYO_NO_URGENTE',
                texto: 'Apoyo no urgente',
                cierra: 'La alerta se cierra como positiva y queda en seguimiento para coordinar el apoyo.',
              },
              { paso: 'EMERGENCIA', texto: 'Emergencia', variante: 'peligro' },
            ])}
          </>
        );
      case 'EMERGENCIA':
        return (
          <>
            <div className="rounded-panel border border-magenta-borde bg-magenta-tinte px-4 py-3 text-[14px] text-magenta">
              <p className="font-bold">Emergencia</p>
              <p className="mt-1">Confirme con el personal en terreno que:</p>
              <ul className="mt-1 list-disc pl-5">
                <li>
                  Llamó al{' '}
                  <a href="tel:131" className="font-bold underline">
                    131 (SAMU)
                  </a>
                  .
                </li>
                <li>No moviliza a la persona si hubo caída con dolor o golpe en la cabeza.</li>
                <li>Permanece con ella hasta que llegue la ambulancia.</li>
              </ul>
            </div>
            <Texto>Indique el resultado cuando lo sepa:</Texto>
            {botones([
              { paso: 'TRASLADADA', texto: 'Trasladada', cierra: 'La alerta se cierra como positiva, derivada a SAMU.' },
              { paso: 'ATENDIDA_EN_CASA', texto: 'Atendida en casa', cierra: 'La alerta se cierra como positiva.' },
              { paso: 'OTRO_RESULTADO', texto: 'Otro resultado', cierra: 'La alerta se cierra como positiva. Después podrá comentar el resultado.' },
            ])}
          </>
        );
      case 'CERRADA':
        return cerrada();
    }
  }

  /** N1-10 si quedó en seguimiento por apoyo no urgente; si no, N1-04 con el comentario del responsable. */
  function cerrada(): ReactNode {
    if (!oficina.cierre) {
      return <Aviso tono="info">El equipo ya atendió esta alerta. No es necesario hacer nada más.</Aviso>;
    }
    if (oficina.resultadoVisita === 'APOYO_NO_URGENTE') {
      return (
        <Aviso tono="info">
          Se registró que la persona requiere apoyo no urgente. La alerta queda <b>en seguimiento</b>.
          <br />
          Coordine el apoyo con el CESFAM y registre las acciones en la {enlaceFicha}. La alerta se cierra desde la
          plataforma.
        </Aviso>
      );
    }
    return (
      <>
        <Aviso tono="exito" titulo={`Alerta #${vista.numero} cerrada`}>
          <ul className="list-disc pl-5">
            <li>Resultado: {resultado(oficina)}.</li>
            <li>Hora de cierre: {formatearFechaHora(oficina.cierre)}.</li>
          </ul>
        </Aviso>
        {oficina.esResponsable && (
          <form
            className="space-y-2"
            onSubmit={(e) => {
              e.preventDefault();
              if (comentario.trim()) dar({ paso: 'COMENTARIO', texto: 'Agregar comentario' }, comentario.trim());
            }}
          >
            <label htmlFor="comentario" className="block text-sm text-apagado">
              Si desea agregar comentarios al resultado de la alerta, escríbalos aquí:
            </label>
            <textarea
              id="comentario"
              value={comentario}
              onChange={(e) => {
                setComentario(e.target.value);
                setComentado(false);
              }}
              maxLength={2000}
              rows={3}
              className="w-full rounded-[5px] border border-linea-oscura bg-superficie px-3 py-2 text-[14px]"
            />
            {comentado && <Aviso tono="exito">Comentario agregado a la ficha.</Aviso>}
            <button
              type="submit"
              disabled={enviando !== null || !comentario.trim()}
              className={`${claseBoton('normal')} w-full py-3 text-base`}
            >
              {enviando === 'COMENTARIO' ? 'Enviando…' : 'Agregar comentario'}
            </button>
          </form>
        )}
      </>
    );
  }
}

/** Texto de un paso, con el tamaño de la página. */
function Texto({ children }: { children: ReactNode }) {
  return <p className="text-[14px] leading-relaxed text-texto">{children}</p>;
}

/** @returns el resultado del cierre en palabras (N1-04) */
function resultado(oficina: VistaOficina): string {
  switch (oficina.clasificacion) {
    case 'POSITIVA':
      return oficina.resultadoVisita === 'EMERGENCIA' ? 'emergencia atendida' : 'el evento sí ocurrió';
    case 'FALSA_ALARMA':
      return 'falsa alarma';
    default:
      return 'cerrada por el equipo';
  }
}
