package cl.ucn.alertacuida.application.paciente.modelo;

import cl.ucn.alertacuida.domain.paciente.NivelDependencia;
import cl.ucn.alertacuida.domain.paciente.Sexo;
import java.time.LocalDate;

/**
 * Identidad de un paciente, tal como se escribe al crearlo o editarlo. Los datos de salud
 * (ficha, condiciones, documentos) van aparte.
 *
 * @param run                 con o sin puntos al entrar; el caso de uso lo normaliza
 * @param alias               nombre corto para los mensajes a los contactos, sin datos clínicos
 * @param fechaNacimiento     opcional; de ella sale la edad
 * @param sexo                opcional
 * @param direccion           opcional
 * @param nivelDependencia    vacío = por evaluar
 * @param fechaConsentimiento cuándo firmó el consentimiento informado; sin ella no se le vincula un reloj
 * @param fechaIngreso        cuándo entró al programa
 */
public record DatosPaciente(String run, String nombres, String apellidos, String alias, LocalDate fechaNacimiento,
		Sexo sexo, String direccion, NivelDependencia nivelDependencia, LocalDate fechaConsentimiento,
		LocalDate fechaIngreso) {

	/** @return los mismos datos con el RUN ya normalizado */
	public DatosPaciente conRun(String runNormalizado) {
		return new DatosPaciente(runNormalizado, nombres, apellidos, alias, fechaNacimiento, sexo, direccion,
				nivelDependencia, fechaConsentimiento, fechaIngreso);
	}

}
