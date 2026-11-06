package cl.ucn.alertacuida.domain.alerta;

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

	/** @return si el reloj puede informarlo (todos menos el silencio de datos y el exceso de alertas) */
	public boolean loDetectaElReloj() {
		return this != SILENCIO_DATOS && this != EXCESO_ALERTAS;
	}

}
