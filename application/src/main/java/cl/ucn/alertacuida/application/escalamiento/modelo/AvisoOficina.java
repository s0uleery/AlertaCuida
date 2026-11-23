package cl.ucn.alertacuida.application.escalamiento.modelo;

import cl.ucn.alertacuida.application.aviso.modelo.Proposito;
import cl.ucn.alertacuida.domain.contacto.Canal;
import java.time.Instant;
import java.util.UUID;

/**
 * Un aviso de una alerta a un coordinador que corresponde dejar en la bandeja de salida y aún no está.
 *
 * @param idAlerta  la alerta
 * @param idUsuario la cuenta COORDINADOR, activa
 * @param proposito el primer aviso o el de agravamiento
 * @param canal     WhatsApp si tiene teléfono y opt-in vigente; si no, correo
 * @param momento   lo que se mide contra el horario laboral: cuándo se recibió la alerta (aviso) o cuándo
 *                  pasó a grave (agravamiento)
 */
public record AvisoOficina(UUID idAlerta, UUID idUsuario, Proposito proposito, Canal canal, Instant momento) {
}
