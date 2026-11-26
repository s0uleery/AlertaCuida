import { formatearFechaHoraExacta } from '../../comun/fechas';
import { CANAL, CLASIFICACION } from './etiquetas';
import type { ClasificacionAlerta, PasoAlerta } from './tipos';

/** Qué le avisa el servidor al reloj, en palabras. */
const AVISO_RELOJ: Record<string, string> = {
  ALERTA_CONFIRMADA: 'la alerta quedó confirmada',
  ALERTA_RESUELTA: 'la alerta quedó resuelta',
  SIMULACRO: 'simulacro',
  CONFIGURACION_NUEVA: 'configuración nueva',
};

/** Cómo se muestran las respuestas de los contactos. */
const RESPUESTA: Record<string, string> = {
  VOY_EN_CAMINO: 'Voy en camino',
  NO_PUEDO: 'No puedo',
};

/** Cómo se muestra el resultado de la llamada de la oficina desde el enlace del correo. */
const LLAMADA_OFICINA: Record<string, string> = {
  BIEN: 'está bien',
  NECESITA_ATENCION: 'necesita atención',
  NO_CONTESTA: 'no contestó',
};

/** Cómo se muestra lo que encontró el personal al llegar. */
const RESULTADO_VISITA: Record<string, string> = {
  BIEN: 'la persona está bien',
  APOYO_NO_URGENTE: 'requiere apoyo no urgente',
  EMERGENCIA: 'emergencia',
};

/** Tono de cada paso: los problemas en rojo, las buenas noticias en verde y el resto en gris. */
type Tono = 'problema' | 'bien' | 'normal';

/** Color del punto de la línea según el tono. */
const PUNTO: Record<Tono, string> = {
  problema: 'bg-magenta',
  bien: 'bg-verde',
  normal: 'bg-linea-oscura',
};

/** @returns "WhatsApp a Ana", "Correo a Ana" o solo el canal si no hay nombre */
function aviso(paso: PasoAlerta): string {
  const canal = paso.canal ? CANAL[paso.canal] : 'Aviso';
  return paso.contacto ? `${canal} a ${paso.contacto}` : canal;
}

/** @returns el tipo de aviso al reloj en palabras; el código tal cual si es uno nuevo */
function avisoReloj(paso: PasoAlerta): string {
  return paso.detalle ? (AVISO_RELOJ[paso.detalle] ?? paso.detalle) : '';
}

/** @returns la frase del paso, su detalle (si tiene) y su tono */
function describir(paso: PasoAlerta): { texto: string; detalle: string | null; tono: Tono } {
  const vuelta = paso.ronda === 2 ? ' (segunda vuelta)' : '';
  switch (paso.tipo) {
    case 'ALERTA_RECIBIDA':
      return { texto: 'Llegó la alerta', detalle: null, tono: 'normal' };
    case 'ALERTA_AGRAVADA':
      return { texto: 'La alerta pasó a grave', detalle: null, tono: 'problema' };
    case 'ALERTA_CANCELADA':
      return { texto: 'El usuario canceló la alerta en el reloj', detalle: null, tono: 'normal' };
    case 'SALTO_CONTACTO':
      return {
        texto: `El reloj pasó a avisar a ${paso.contacto ?? 'otro contacto'}${vuelta}`,
        detalle: null,
        tono: 'normal',
      };
    case 'AVISO_ENVIADO':
      return { texto: `${aviso(paso)}: enviado${vuelta}`, detalle: null, tono: 'normal' };
    case 'AVISO_ENTREGADO':
      return { texto: `${aviso(paso)}: entregado`, detalle: null, tono: 'normal' };
    case 'AVISO_LEIDO':
      return { texto: `${aviso(paso)}: leído`, detalle: null, tono: 'normal' };
    case 'AVISO_FALLIDO':
      return { texto: `${aviso(paso)}: no se pudo enviar`, detalle: paso.detalle, tono: 'problema' };
    case 'RESPUESTA_CONTACTO': {
      const respuesta = paso.detalle ? (RESPUESTA[paso.detalle] ?? paso.detalle) : '';
      return {
        texto: `${paso.contacto ?? 'El contacto'} respondió: ${respuesta}`,
        detalle: null,
        tono: paso.detalle === 'VOY_EN_CAMINO' ? 'bien' : 'problema',
      };
    }
    case 'LLAMADA_PACIENTE':
      return {
        texto: `${paso.contacto ?? 'El contacto'} intentó llamar al usuario desde el enlace`,
        detalle: null,
        tono: 'normal',
      };
    case 'LLEGADA_CONTACTO':
      return { texto: `${paso.contacto ?? 'El contacto'} llegó donde el usuario`, detalle: null, tono: 'bien' };
    case 'RESULTADO_LLAMADA':
      return {
        texto: `${paso.contacto ?? 'El contacto'} indicó que el usuario ${paso.detalle === 'CONTESTA' ? 'contestó' : 'no contestó'} la llamada`,
        detalle: null,
        tono: paso.detalle === 'CONTESTA' ? 'normal' : 'problema',
      };
    case 'LLAMADA_OFICINA':
      return {
        texto: `${paso.usuario ?? 'La oficina'} llamó al usuario: ${LLAMADA_OFICINA[paso.detalle ?? ''] ?? paso.detalle}`,
        detalle: null,
        tono: paso.detalle === 'BIEN' ? 'bien' : 'problema',
      };
    case 'PERSONAL_ASIGNADO':
      return { texto: `${paso.usuario ?? 'La oficina'} asignó personal para ir al domicilio`, detalle: null, tono: 'bien' };
    case 'SIN_DISPONIBILIDAD':
      return {
        texto: `${paso.usuario ?? 'La oficina'} no tenía personal disponible: el reloj sigue avisando a los contactos`,
        detalle: null,
        tono: 'problema',
      };
    case 'LLEGADA_PERSONAL':
      return { texto: 'El personal asignado llegó al domicilio', detalle: null, tono: 'bien' };
    case 'RESULTADO_VISITA':
      return {
        texto: `Resultado de la visita: ${RESULTADO_VISITA[paso.detalle ?? ''] ?? paso.detalle}`,
        detalle: null,
        tono: paso.detalle === 'EMERGENCIA' ? 'problema' : 'normal',
      };
    case 'SIN_RESPUESTA':
      return { texto: 'El reloj terminó la lista y nadie confirmó', detalle: null, tono: 'problema' };
    case 'CASO_ASUMIDO':
      return { texto: `${paso.usuario ?? 'Alguien del equipo'} asumió el caso`, detalle: null, tono: 'normal' };
    case 'SEGUIMIENTO':
      return { texto: `Seguimiento de ${paso.usuario ?? 'el equipo'}`, detalle: paso.detalle, tono: 'normal' };
    case 'ALERTA_RESUELTA':
      return {
        texto: paso.usuario
          ? `${paso.usuario} resolvió la alerta`
          : paso.contacto
            ? `Alerta resuelta por la llegada de ${paso.contacto}`
            : 'El sistema cerró la alerta',
        detalle: paso.detalle ? (CLASIFICACION[paso.detalle as ClasificacionAlerta] ?? paso.detalle) : null,
        tono: 'bien',
      };
    case 'SEGUIMIENTO_CERRADO':
      return { texto: 'Se cerró el seguimiento', detalle: null, tono: 'bien' };
    case 'FCM_PENDIENTE':
      return { texto: `Aviso al reloj en cola: ${avisoReloj(paso)}`, detalle: null, tono: 'normal' };
    case 'FCM_ENVIADO':
      return { texto: `Aviso enviado al reloj: ${avisoReloj(paso)}`, detalle: null, tono: 'normal' };
    case 'FCM_RECIBIDO':
      return { texto: `El reloj recibió el aviso: ${avisoReloj(paso)}`, detalle: null, tono: 'bien' };
    case 'FCM_NO_CONFIRMADO':
      return { texto: `El reloj no confirmó el aviso: ${avisoReloj(paso)}`, detalle: null, tono: 'problema' };
  }
}

/**
 * Recorrido de la alerta, del paso más antiguo al más reciente: lo que pidió el reloj, cada WhatsApp y
 * correo con su entrega, las respuestas, los avisos al reloj y lo que hizo el equipo. Las horas son las
 * registradas: lo que informa el reloj usa la hora del reloj.
 */
export function LineaTiempo({ pasos }: { pasos: PasoAlerta[] }) {
  if (pasos.length === 0) return <p className="text-apagado">Todavía no hay pasos registrados.</p>;
  return (
    <ol className="relative space-y-4 border-l-2 border-linea pl-6">
      {pasos.map((paso, i) => {
        const { texto, detalle, tono } = describir(paso);
        return (
          <li key={`${paso.fechaHora}-${paso.tipo}-${i}`} className="relative">
            <span
              aria-hidden="true"
              className={`absolute -left-[31px] top-1.5 h-3 w-3 rounded-full ring-4 ring-white ${PUNTO[tono]}`}
            />
            <p className={tono === 'problema' ? 'font-medium text-magenta' : 'text-tinta'}>{texto}</p>
            {detalle && <p className="text-sm text-apagado">{detalle}</p>}
            <p className="text-xs text-apagado">{formatearFechaHoraExacta(paso.fechaHora)}</p>
          </li>
        );
      })}
    </ol>
  );
}
