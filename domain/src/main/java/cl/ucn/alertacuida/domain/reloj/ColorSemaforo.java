package cl.ucn.alertacuida.domain.reloj;

/** Color del semáforo de un reloj en el dashboard. */
public enum ColorSemaforo {
	/** Funcionando: nada que revisar. */
	VERDE,
	/** Atención: algo hay que revisar, pero el reloj sigue enviando datos. */
	AMARILLO,
	/** Sin señal: no sincroniza hace tiempo o su plan de datos venció. */
	ROJO,
	/** Sin vincular: nadie lo usa. */
	GRIS
}
