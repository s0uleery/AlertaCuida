package cl.ucn.alertacuida.domain.reloj;

import java.time.Duration;

/**
 * Umbrales del semáforo. Los cuatro primeros son los de las reglas de ausencia, para que el semáforo y las
 * alertas técnicas no se contradigan.
 *
 * @param silencio      tiempo sin sincronizar desde el cual no hay señal (rojo)
 * @param bateriaMinima porcentaje desde el cual la batería ya no es baja
 * @param horaInicioDia hora de Chile (incluida) desde la que cuenta "no puesto"
 * @param horaFinDia    hora de Chile (excluida) hasta la que cuenta "no puesto"
 * @param atraso        tiempo sin sincronizar, menor que el silencio, desde el cual se marca atraso (sin incluirlo)
 * @param diasAvisoPlan con cuántos días de anticipación se avisa que el plan de datos vence
 */
public record UmbralesSemaforo(Duration silencio, int bateriaMinima, int horaInicioDia, int horaFinDia,
		Duration atraso, int diasAvisoPlan) {
}
