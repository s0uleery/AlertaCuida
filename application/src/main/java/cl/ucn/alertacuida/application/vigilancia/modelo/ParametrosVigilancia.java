package cl.ucn.alertacuida.application.vigilancia.modelo;

import java.time.Duration;

/**
 * Valores de las reglas de ausencia.
 *
 * @param silencio      tiempo sin sincronizar desde el cual hay silencio de datos
 * @param bateriaMinima porcentaje desde el cual la batería ya no se considera baja
 * @param horaInicioDia hora de Chile (incluida) en que parte la revisión de reloj no puesto
 * @param horaFinDia    hora de Chile (excluida) en que termina esa revisión
 */
public record ParametrosVigilancia(Duration silencio, int bateriaMinima, int horaInicioDia, int horaFinDia) {
}
