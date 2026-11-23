package cl.ucn.alertacuida.application.tendencia.modelo;

/** Cómo se juntan las lecturas según el largo del rango (decisión del 19-11-2026). */
public enum Agrupacion {
	/** Hasta 2 días: cada lectura con su hora de medición. */
	LECTURA,
	/** Hasta 31 días: por hora de Chile. */
	HORA,
	/** Hasta 90 días: por día de Chile. */
	DIA
}
