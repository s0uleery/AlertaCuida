package cl.ucn.alertacuida.application.contacto.modelo;

import cl.ucn.alertacuida.domain.contacto.Horario;

/**
 * Lo que depende del vínculo entre una persona y un paciente (tabla contacto_paciente): una misma
 * persona puede ser cuidadora de un paciente y vecina de otro, con otro horario.
 *
 * @param tipo              código de tipo_contacto
 * @param parentesco        solo si el tipo es FAMILIA, o vacío
 * @param cuidadorPrincipal si es el cuidador principal del paciente
 * @param horario           cuándo recibe avisos; vacío = todo el día
 */
public record Vinculo(String tipo, String parentesco, boolean cuidadorPrincipal, Horario horario) {
}
