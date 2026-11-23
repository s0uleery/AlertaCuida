package cl.ucn.alertacuida.application.clinica.modelo;

import java.time.Instant;

/**
 * Lo que rige hoy para un paciente: sus ajustes, umbrales y zona segura, con los valores base donde
 * no tiene ajustes propios. Es lo que verá el reloj en su configuración.
 *
 * @param ajustes       módulos y opciones (los por defecto si nunca se configuró)
 * @param ajustesDesde  desde cuándo rigen los ajustes; vacío si nunca se configuró
 * @param minutosEspera los minutos de espera que rigen: los del paciente o el valor base
 * @param fc            umbral de la frecuencia cardíaca
 * @param spo2          umbral de la saturación de oxígeno
 * @param zonaSegura    la zona guardada, aunque el módulo esté apagado; vacía si nunca se definió
 */
public record PerfilClinico(AjustesClinicos ajustes, Instant ajustesDesde, int minutosEspera, UmbralVigente fc,
		UmbralVigente spo2, ZonaGuardada zonaSegura) {
}
