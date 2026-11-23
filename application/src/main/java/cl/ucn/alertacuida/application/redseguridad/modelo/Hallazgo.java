package cl.ucn.alertacuida.application.redseguridad.modelo;

import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Una lectura más allá del límite grave sin alerta del reloj cerca: un posible evento que el reloj no alertó.
 * No se guarda ni avisa a nadie; sirve para auditar el reloj.
 *
 * @param paciente          de quién es la lectura
 * @param variable          FC o SPO2
 * @param valor             lo que midió el reloj
 * @param fechaHoraMedicion la hora del reloj al medir
 * @param limiteGrave       el límite grave que superó, vigente al medir
 */
public record Hallazgo(PacienteDeHallazgo paciente, TipoLectura variable, double valor, Instant fechaHoraMedicion,
		BigDecimal limiteGrave) {

	/**
	 * El paciente de un hallazgo, con sus datos ya descifrados.
	 *
	 * @param id        para enlazar a su ficha
	 * @param nombres   puede ser null si no se registró
	 * @param apellidos puede ser null si no se registró
	 */
	public record PacienteDeHallazgo(UUID id, String nombres, String apellidos) {
	}

}
