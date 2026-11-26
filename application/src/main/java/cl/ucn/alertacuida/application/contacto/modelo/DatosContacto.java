package cl.ucn.alertacuida.application.contacto.modelo;

import cl.ucn.alertacuida.domain.alerta.Severidad;
import java.time.LocalTime;
import java.util.Set;

/**
 * Lo que el equipo ingresa al agregar o editar un contacto de un paciente, tal como llega:
 * el caso de uso lo valida y normaliza.
 *
 * @param nombre            nombre de la persona o institución
 * @param whatsapp          número de WhatsApp (opcional si hay correo)
 * @param correo            correo (opcional si hay WhatsApp)
 * @param tipo              código de tipo_contacto (TERCERO_SIGNIFICATIVO, CARABINEROS, …)
 * @param parentesco        ej. hija, sobrino; solo si el tipo es TERCERO_SIGNIFICATIVO
 * @param cuidadorPrincipal si es el cuidador principal del paciente (uno por paciente)
 * @param horaInicio        desde qué hora recibe avisos; vacía junto con horaFin = todo el día
 * @param horaFin           hasta qué hora recibe avisos
 * @param gravedades        de qué alertas recibe avisos (al menos una); al editar un contacto desactivado no se
 *                          usan, porque se eligen al reactivarlo
 */
public record DatosContacto(String nombre, String whatsapp, String correo, String tipo, String parentesco,
		boolean cuidadorPrincipal, LocalTime horaInicio, LocalTime horaFin, Set<Severidad> gravedades) {
}
