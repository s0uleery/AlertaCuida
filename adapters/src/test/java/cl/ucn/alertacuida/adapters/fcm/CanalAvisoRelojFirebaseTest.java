package cl.ucn.alertacuida.adapters.fcm;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.avisoreloj.error.TokenRelojRechazado;
import cl.ucn.alertacuida.application.avisoreloj.modelo.AvisoReloj;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Prueba contra el proyecto de Firebase real, sin reloj. Solo corre si FCM_CREDENCIALES apunta a la llave
 * de la cuenta de servicio (fuera del repositorio); en las pruebas normales y en GitHub se omite. Necesita
 * internet.
 *
 * <p>Requisito (cronograma, 2.3.1): el backend se autentica con la llave y Firebase acepta sus mensajes.
 * Como no hay un token de reloj real, se envía a un token inventado: si la llave sirve, Firebase autentica
 * y después rechaza el token, y el canal lo informa como token rechazado. Con una llave mala el error
 * sería otro.
 */
@EnabledIfEnvironmentVariable(named = "FCM_CREDENCIALES", matches = ".+")
class CanalAvisoRelojFirebaseTest {

	/** La llave autentica y un token inventado vuelve como token rechazado. */
	@Test
	void conLaLlaveRealUnTokenInventadoEsRechazado() {
		CanalAvisoRelojFirebase canal = CanalAvisoRelojFirebase.desdeArchivo(System.getenv("FCM_CREDENCIALES"));

		assertThatThrownBy(() -> canal.enviar(AvisoReloj.configuracionNueva("token-inventado-de-prueba")))
				.isInstanceOf(TokenRelojRechazado.class);
	}

}
