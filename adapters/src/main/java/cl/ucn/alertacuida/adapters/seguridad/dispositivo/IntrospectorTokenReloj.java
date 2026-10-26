package cl.ucn.alertacuida.adapters.seguridad.dispositivo;

import cl.ucn.alertacuida.application.asignacion.AutenticarReloj;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.stereotype.Component;

/**
 * Revisa el token del reloj en cada petición a /api/reloj. Para Spring es un token "opaco": no es
 * un JWT con datos adentro, sino un valor aleatorio que solo la base reconoce (por su hash).
 */
@Component
public class IntrospectorTokenReloj implements OpaqueTokenIntrospector {

	private final AutenticarReloj autenticar;

	/** @param autenticar caso de uso que busca el vínculo vigente del token */
	public IntrospectorTokenReloj(AutenticarReloj autenticar) {
		this.autenticar = autenticar;
	}

	/**
	 * @param token el que vino después de "Bearer "
	 * @return el reloj de ese token
	 * @throws BadOpaqueTokenException si el token no es de un vínculo vigente (Spring responde 401)
	 */
	@Override
	public PrincipalReloj introspect(String token) throws OAuth2IntrospectionException {
		return autenticar.autenticar(token)
				.map(PrincipalReloj::new)
				.orElseThrow(() -> new BadOpaqueTokenException("Token del reloj desconocido o revocado"));
	}

}
