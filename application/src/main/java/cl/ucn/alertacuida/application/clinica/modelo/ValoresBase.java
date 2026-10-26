package cl.ucn.alertacuida.application.clinica.modelo;

import cl.ucn.alertacuida.domain.clinica.Umbral;
import cl.ucn.alertacuida.domain.telemetria.TipoLectura;

/**
 * Valores del sistema que rigen cuando el paciente no tiene ajustes propios (Anexo B). Vienen de
 * application.properties: cambiarlos requiere un despliegue, no es un dato de la base.
 *
 * @param umbralFc      umbral base de la frecuencia cardíaca
 * @param umbralSpo2    umbral base de la saturación de oxígeno
 * @param minutosEspera antes de pasar al siguiente contacto
 */
public record ValoresBase(Umbral umbralFc, Umbral umbralSpo2, int minutosEspera) {

	/** @return el umbral base de esa variable */
	public Umbral umbral(TipoLectura variable) {
		return variable == TipoLectura.FC ? umbralFc : umbralSpo2;
	}

}
