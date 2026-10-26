package cl.ucn.alertacuida.adapters.web.clinica;

import cl.ucn.alertacuida.application.clinica.modelo.AjustesClinicos;
import cl.ucn.alertacuida.application.clinica.modelo.Modulos;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PUT /api/pacientes/{id}/perfil-clinico/ajustes}. Aquí solo se revisa la forma;
 * los rangos están en el caso de uso.
 *
 * @param modulos         qué vigila el reloj
 * @param gpsActivo       si el reloj envía la ubicación
 * @param modoConservador si el reloj alerta con menos evidencia
 * @param minutosEspera   antes de pasar al siguiente contacto; vacío = valor base
 * @param motivo          por qué se cambia (opcional)
 */
public record SolicitudAjustes(@NotNull Modulos modulos, @NotNull Boolean gpsActivo, @NotNull Boolean modoConservador,
		Integer minutosEspera, @Size(max = 500) String motivo) {

	/** @return los ajustes para el caso de uso */
	public AjustesClinicos ajustes() {
		return new AjustesClinicos(modulos, gpsActivo, modoConservador, minutosEspera, motivo);
	}

}
