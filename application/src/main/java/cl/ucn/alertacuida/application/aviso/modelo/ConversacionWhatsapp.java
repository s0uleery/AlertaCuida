package cl.ucn.alertacuida.application.aviso.modelo;

import cl.ucn.alertacuida.domain.alerta.EstadoContacto;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import java.time.Instant;
import java.util.UUID;

/**
 * Estado actual de una conversación de WhatsApp y el contexto mínimo de su alerta para decidir la transición.
 *
 * @param idConversacion        identificador propio de la conversación
 * @param idIntento             intento de registro_intento asociado
 * @param idAlerta              alerta correspondiente
 * @param numeroAlerta          folio legible (#142)
 * @param aliasPaciente         alias para referirse al paciente sin exponer datos sensibles
 * @param telefonoReloj         número SIM del reloj para recordar al contacto a quién llamar (puede ser null)
 * @param severidad             severidad actual de la alerta (define frecuencia y tope de recordatorios)
 * @param estadoAlerta          estado calculado de la alerta en alerta_estado
 * @param delReloj              si la alerta fue originada por el reloj (requiere aviso FCM de cambio de estado)
 * @param nivel                 Nivel 1 PRLAC, Nivel 2 Tercero, Nivel 3 Servicio o Simulacro
 * @param etapa                 etapa actual de la máquina de estados
 * @param ultimoWamid           wamid del último mensaje enviado en esta conversación
 * @param recordatoriosEnviados cuántos recordatorios de llegada se han enviado
 * @param proximoVencimiento    cuándo vence el plazo actual (llamada o siguiente recordatorio)
 * @param ventana24hHasta       hasta cuándo está abierta la ventana de atención de 24 h de Meta
 */
public record ConversacionWhatsapp(UUID idConversacion, UUID idIntento, UUID idAlerta, long numeroAlerta,
		String aliasPaciente, String telefonoReloj, Severidad severidad, EstadoContacto estadoAlerta,
		boolean delReloj, NivelConversacion nivel, EtapaConversacion etapa, String ultimoWamid,
		int recordatoriosEnviados, Instant proximoVencimiento, Instant ventana24hHasta) {

	/** @return si en el instante indicado la ventana de 24 h de Meta sigue abierta */
	public boolean ventanaAbierta(Instant ahora) {
		return ventana24hHasta != null && ahora.isBefore(ventana24hHasta);
	}

}
