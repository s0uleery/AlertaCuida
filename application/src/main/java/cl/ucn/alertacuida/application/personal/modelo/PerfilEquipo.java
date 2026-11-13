package cl.ucn.alertacuida.application.personal.modelo;

import java.time.Instant;
import java.util.UUID;

/**
 * Una cuenta del equipo tal como la muestra la sección Administración. No trae el RUN ni el
 * teléfono, que van cifrados: solo si hay teléfono, para saber si hay que pedirlo al pasar a coordinador.
 *
 * @param cargo         opcional
 * @param ultimoAcceso  último LOGIN en la auditoría; vacío si nunca ha entrado
 * @param recibeAvisos  si recibe por correo los avisos de las alertas técnicas
 * @param tieneTelefono si tiene un celular con WhatsApp registrado
 */
public record PerfilEquipo(UUID id, String nombre, String cargo, String correo, String codigoRol, EstadoCuenta estado,
		Instant ultimoAcceso, boolean recibeAvisos, boolean tieneTelefono) {
}
