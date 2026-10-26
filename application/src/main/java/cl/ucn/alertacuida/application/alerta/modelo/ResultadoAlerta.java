package cl.ucn.alertacuida.application.alerta.modelo;

/**
 * @param respuesta la misma si la alerta es nueva o si el reloj la reenvió
 * @param nueva     false si ya estaba guardada (el reloj reintentó): el controlador responde 200 en vez de 201
 */
public record ResultadoAlerta(RespuestaAlerta respuesta, boolean nueva) {
}
