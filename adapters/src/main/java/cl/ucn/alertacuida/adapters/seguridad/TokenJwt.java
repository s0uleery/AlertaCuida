package cl.ucn.alertacuida.adapters.seguridad;

import cl.ucn.alertacuida.application.sesion.modelo.TokenAcceso;
import cl.ucn.alertacuida.application.sesion.modelo.UsuarioSesion;
import cl.ucn.alertacuida.application.sesion.puerto.EmisorToken;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Formato del token de acceso: el único lugar que conoce sus claims. Spring Security valida la firma
 * y la expiración; aquí solo se arma y se lee. Los permisos viajan en el token, así que un cambio de
 * permisos se nota al iniciar sesión de nuevo (como máximo, lo que dura el token).
 */
@Component
public class TokenJwt implements EmisorToken {

	static final String EMISOR = "alertacuida";
	static final String CLAIM_NOMBRE = "nombre";
	static final String CLAIM_ROL = "rol";
	static final String CLAIM_PERMISOS = "permisos";

	private final JwtEncoder encoder;
	private final PropiedadesSeguridad propiedades;
	private final Clock reloj;

	/**
	 * @param encoder     firma con la clave de {@link ConfiguracionSeguridad}
	 * @param propiedades de ahí sale la duración del token
	 * @param reloj       hora actual
	 */
	public TokenJwt(JwtEncoder encoder, PropiedadesSeguridad propiedades, Clock reloj) {
		this.encoder = encoder;
		this.propiedades = propiedades;
		this.reloj = reloj;
	}

	/**
	 * Arma y firma el token: el "sub" es el id del usuario, y lleva su nombre, rol y permisos.
	 *
	 * @param usuario quién inició sesión
	 * @return el token firmado (HS256) y su vencimiento
	 */
	@Override
	public TokenAcceso emitir(UsuarioSesion usuario) {
		Instant ahora = reloj.instant();
		Instant expira = ahora.plus(propiedades.duracionToken());
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(EMISOR)
				.subject(usuario.id().toString())
				.issuedAt(ahora)
				.expiresAt(expira)
				.claim(CLAIM_NOMBRE, usuario.nombre())
				.claim(CLAIM_ROL, usuario.codigoRol())
				.claim(CLAIM_PERMISOS, usuario.permisos().stream().sorted().toList())
				.build();
		JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
		String valor = encoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
		return new TokenAcceso(valor, expira);
	}

	/** El usuario de una petición autenticada, sin ir a la base. */
	public static UsuarioSesion leer(Jwt jwt) {
		List<String> permisos = jwt.getClaimAsStringList(CLAIM_PERMISOS);
		return new UsuarioSesion(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString(CLAIM_NOMBRE),
				jwt.getClaimAsString(CLAIM_ROL), new HashSet<>(permisos == null ? List.of() : permisos));
	}

}
