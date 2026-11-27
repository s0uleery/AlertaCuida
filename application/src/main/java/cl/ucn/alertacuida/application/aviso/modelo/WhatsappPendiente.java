package cl.ucn.alertacuida.application.aviso.modelo;

import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.alerta.TipoEvento;
import java.time.Instant;
import java.util.UUID;

/**
 * Un WhatsApp de la bandeja de salida al que le toca salir, con todo lo necesario para elegir y rellenar la
 * plantilla aprobada de Meta y abrir su conversación.
 *
 * @param idIntento       su fila en registro_intento
 * @param idAlerta        la alerta a la que pertenece
 * @param telefono        número del contacto o de la cuenta, ya descifrado; null si ya no tiene
 * @param numero          folio de la alerta
 * @param alias           nombre corto del paciente para los mensajes; puede faltar
 * @param nombreCompleto  nombre y apellidos del paciente, descifrados; para plantillas de Nivel 3 y simulacro
 * @param edad            edad calculada en hora de Chile; puede ser null si falta fecha de nacimiento
 * @param rut             RUN del paciente, descifrado; para plantilla de Carabineros y simulacro
 * @param direccion       dirección del paciente, descifrada; se usa en nota_adicional
 * @param telefonoReloj   número de la SIM del reloj; para plantillas de Nivel 1 y 2
 * @param tipo            qué se detectó
 * @param severidad       gravedad actual (grave si una caída se agravó)
 * @param fechaHoraEvento hora de detección o recepción de la alerta
 * @param ubicacion       coordenadas de la alerta (con respaldo de sincronización o zona segura); puede ser null
 * @param esSimulacro     si es un simulacro (plantilla simulacro_aviso_inicial)
 * @param reintentos      cuántos reintentos ya se hicieron
 * @param alEquipo        si va a una cuenta del equipo PRLAC (aviso a la oficina) o a un contacto del paciente
 * @param tipoContacto    código de tipo_contacto (PRLAC, TERCERO_SIGNIFICATIVO, CARABINEROS…); null si es interno
 * @param proposito       un aviso o el de agravamiento
 */
public record WhatsappPendiente(UUID idIntento, UUID idAlerta, String telefono, long numero, String alias,
		String nombreCompleto, Integer edad, String rut, String direccion, String telefonoReloj, TipoEvento tipo,
		Severidad severidad, Instant fechaHoraEvento, UbicacionWhatsapp ubicacion, boolean esSimulacro,
		int reintentos, boolean alEquipo, String tipoContacto, Proposito proposito) {

	/** Constructor compacto para pruebas unitarias de un aviso a un tercero significativo. */
	public WhatsappPendiente(UUID idIntento, String telefono, long numero, String alias, TipoEvento tipo,
			Severidad severidad, boolean esSimulacro, int reintentos) {
		this(idIntento, UUID.randomUUID(), telefono, numero, alias, alias, 80, "12.345.678-5",
				"Av. Costanera 123, Coquimbo", "+56987654321", tipo, severidad,
				Instant.parse("2026-11-26T18:00:00Z"),
				new UbicacionWhatsapp(-29.9533, -71.3395, "Ubicación del paciente", "Av. Costanera 123, Coquimbo"),
				esSimulacro, reintentos, false, "TERCERO_SIGNIFICATIVO", Proposito.AVISO);
	}

	/** @return el nivel conversacional correspondiente a este aviso */
	public NivelConversacion nivel() {
		return NivelConversacion.de(esSimulacro, alEquipo, tipoContacto);
	}

}
