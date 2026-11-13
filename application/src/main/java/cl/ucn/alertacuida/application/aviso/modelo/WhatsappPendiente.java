package cl.ucn.alertacuida.application.aviso.modelo;

import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.util.UUID;

/**
 * Un WhatsApp de la bandeja de salida al que le toca salir, con lo necesario para la plantilla.
 *
 * @param idIntento   su fila en registro_intento
 * @param telefono    número del contacto, ya descifrado; null si ya no tiene
 * @param numero      folio de la alerta
 * @param alias       nombre corto del paciente para los mensajes; puede faltar
 * @param tipo        qué se detectó
 * @param severidad   gravedad actual (grave si una caída se agravó)
 * @param esSimulacro si es un simulacro (plantilla "PRUEBA")
 * @param reintentos  cuántos reintentos ya se hicieron
 */
public record WhatsappPendiente(UUID idIntento, String telefono, long numero, String alias, TipoEvento tipo,
		Severidad severidad, boolean esSimulacro, int reintentos) {
}
