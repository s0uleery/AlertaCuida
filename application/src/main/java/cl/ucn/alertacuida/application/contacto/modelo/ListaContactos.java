package cl.ucn.alertacuida.application.contacto.modelo;

import cl.ucn.alertacuida.domain.contacto.Cobertura;
import java.util.List;

/**
 * La lista de contactos de un paciente, con las horas en que nadie puede recibir avisos.
 *
 * @param contactos    primero los activos por prioridad, luego los desactivados por nombre
 * @param sinCobertura tramos del día sin ningún contacto disponible; vacío si está cubierto. Es un
 *                     aviso: las alertas medias y graves avisan a toda hora
 */
public record ListaContactos(List<ContactoDelPaciente> contactos, List<Cobertura.Tramo> sinCobertura) {
}
