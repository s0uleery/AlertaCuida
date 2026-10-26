package cl.ucn.alertacuida.application.clinica.modelo;

import java.util.Objects;

/**
 * Lo que se guarda en una fila de la tabla configuracion: los módulos y opciones del paciente.
 *
 * @param modulos         qué vigila el reloj
 * @param gpsActivo       si el reloj envía la ubicación (solo para ubicar a la persona; no genera alertas)
 * @param modoConservador si el reloj alerta antes, con menos evidencia
 * @param minutosEspera   antes de pasar al siguiente contacto; vacío = el valor base
 * @param motivo          por qué se cambió (opcional); se guarda cifrado
 */
public record AjustesClinicos(Modulos modulos, boolean gpsActivo, boolean modoConservador, Integer minutosEspera,
		String motivo) {

	/** Los que rigen mientras el paciente no tiene configuración (valores por defecto de V3). */
	public static final AjustesClinicos POR_DEFECTO = new AjustesClinicos(Modulos.POR_DEFECTO, false, false, null,
			null);

	/** @return si dicen lo mismo que otros, sin contar el motivo (que no cambia lo que hace el reloj) */
	public boolean igualesA(AjustesClinicos otros) {
		return modulos.equals(otros.modulos) && gpsActivo == otros.gpsActivo
				&& modoConservador == otros.modoConservador
				&& Objects.equals(minutosEspera, otros.minutosEspera);
	}

}
