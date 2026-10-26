package cl.ucn.alertacuida.adapters.web.paciente;

import cl.ucn.alertacuida.application.paciente.modelo.DatosPaciente;
import cl.ucn.alertacuida.domain.paciente.NivelDependencia;
import cl.ucn.alertacuida.domain.paciente.Sexo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Cuerpo de {@code POST /api/pacientes} y {@code PUT /api/pacientes/{id}}. Las fechas van como
 * "2026-10-22". Aquí solo se revisa la forma; las reglas (RUN válido, fechas no futuras, RUN
 * repetido) están en los casos de uso.
 *
 * @param run                 con o sin puntos
 * @param alias               opcional: nombre corto para los mensajes, sin datos clínicos
 * @param fechaNacimiento     opcional
 * @param sexo                opcional: FEMENINO o MASCULINO
 * @param direccion           opcional
 * @param nivelDependencia    opcional: MODERADA o SEVERA (vacío = por evaluar)
 * @param fechaConsentimiento opcional: sin ella no se le puede vincular un reloj
 */
public record SolicitudPaciente(
		@NotBlank String run,
		@NotBlank @Size(max = 200) String nombres,
		@NotBlank @Size(max = 200) String apellidos,
		@Size(max = 60) String alias,
		LocalDate fechaNacimiento,
		Sexo sexo,
		@Size(max = 300) String direccion,
		NivelDependencia nivelDependencia,
		LocalDate fechaConsentimiento,
		@NotNull LocalDate fechaIngreso) {

	/** @return los mismos datos en el formato del caso de uso */
	DatosPaciente datos() {
		return new DatosPaciente(run, nombres, apellidos, alias, fechaNacimiento, sexo, direccion, nivelDependencia,
				fechaConsentimiento, fechaIngreso);
	}

}
