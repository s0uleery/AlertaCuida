package cl.ucn.alertacuida.adapters.seguridad.hash;

import cl.ucn.alertacuida.application.asignacion.puerto.CodigosCortos;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Códigos de emparejamiento del reloj: 8 caracteres de un alfabeto de 31 (sin 0, O, 1, I ni L, que se
 * confunden al leerlos en la pantalla del reloj). Son unos 850 mil millones de códigos posibles: con
 * pocos pendientes a la vez y 10 minutos de vigencia, adivinar uno no es práctico, y además escribirlo
 * exige una sesión del dashboard con USUARIOS:EDITAR.
 */
@Component
public class CodigosCortosAleatorios implements CodigosCortos {

	private static final String ALFABETO = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
	private static final int LARGO = 8;

	/** SecureRandom es seguro entre hilos: uno para toda la aplicación. */
	private final SecureRandom aleatorio = new SecureRandom();

	@Override
	public String nuevo() {
		StringBuilder codigo = new StringBuilder(LARGO);
		for (int i = 0; i < LARGO; i++) {
			codigo.append(ALFABETO.charAt(aleatorio.nextInt(ALFABETO.length())));
		}
		return codigo.toString();
	}

}
