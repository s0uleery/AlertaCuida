package cl.ucn.alertacuida.domain.alerta;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Qué se detectó (alerta.tipo_evento y evento_descartado.tipo_evento). */
public enum TipoEvento {
	CAIDA,
	FC,
	SPO2,
	FC_SPO2,
	TENDENCIA,
	INACTIVIDAD,
	/** La persona salió del círculo de su zona segura; la severidad la define su zona. */
	ZONA_SEGURA,
	/** La persona presionó el botón de ayuda del reloj. Siempre leve: avisa primero a los vecinos. */
	BOTON_AYUDA,
	/** El reloj dejó de sincronizar: solo lo detecta el servidor. */
	SILENCIO_DATOS,
	BATERIA_BAJA,
	RELOJ_NO_PUESTO,
	/** El reloj envió demasiadas alertas en poco tiempo (posible falla): solo lo detecta el servidor. */
	EXCESO_ALERTAS;

	/** Las que hablan del reloj y no de la salud de la persona. */
	private static final Set<TipoEvento> TECNICAS = Collections.unmodifiableSet(
			EnumSet.of(SILENCIO_DATOS, BATERIA_BAJA, RELOJ_NO_PUESTO, EXCESO_ALERTAS));

	/** @return si el reloj puede informarlo (todos menos el silencio de datos y el exceso de alertas) */
	public boolean loDetectaElReloj() {
		return this != SILENCIO_DATOS && this != EXCESO_ALERTAS;
	}

	/**
	 * @return si es técnica: habla del reloj y la avisa el servidor al equipo PRLAC. Las demás son de
	 *         salud: las enruta el reloj a los contactos del paciente
	 */
	public boolean esTecnica() {
		return TECNICAS.contains(this);
	}

	/** @return los tipos técnicos (no se puede modificar) */
	public static Set<TipoEvento> tecnicas() {
		return TECNICAS;
	}

}
