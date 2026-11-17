package cl.ucn.alertacuida.application.escalamiento.modelo;

import cl.ucn.alertacuida.domain.contacto.Canal;
import java.util.UUID;

/**
 * Lo que pide el reloj al avisar a un contacto (esquema SolicitudAviso de docs/api-reloj.yaml).
 *
 * @param eventId    de esta solicitud; repetirlo no envía dos veces
 * @param idContacto el vínculo con el paciente (contacto_paciente), el que viene en la configuración
 * @param canal      WHATSAPP o CORREO
 * @param ronda      1 o 2: el reloj recorre la lista hasta dos veces
 */
public record SolicitudAviso(UUID eventId, UUID idContacto, Canal canal, Integer ronda) {
}
