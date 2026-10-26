package cl.ucn.alertacuida.adapters.seguridad;

import cl.ucn.alertacuida.adapters.seguridad.dispositivo.EntradaNoAutorizadaReloj;
import cl.ucn.alertacuida.adapters.seguridad.dispositivo.IntrospectorTokenReloj;
import cl.ucn.alertacuida.adapters.seguridad.dispositivo.PrincipalReloj;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad del dashboard y de la API del reloj, cada uno con su propia cadena de filtros y su
 * propio token. Sin sesión en el servidor: cada petición trae el token en la cabecera
 * Authorization: Bearer. Por eso no hace falta CSRF (no hay cookies que el navegador mande solo).
 * Cada permiso del token ("ALERTAS:VER") es una authority.
 *
 * <p>Autorización en dos niveles:
 * <ol>
 * <li>Aquí: todo exige un token válido, salvo las pocas rutas públicas (sin token responde 401).</li>
 * <li>En cada endpoint: {@code @PreAuthorize("hasAuthority('MODULO:ACCION')")} exige el permiso
 * (sin él responde 403). Lo habilita {@link EnableMethodSecurity}.</li>
 * </ol>
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(PropiedadesSeguridad.class)
public class ConfiguracionSeguridad {

	/**
	 * Reglas de la API del reloj (/api/reloj/**), aparte de las del dashboard: solo acepta el token
	 * del reloj (rol DEVICE), que se revisa contra la base en cada petición. Va primera: las
	 * peticiones del reloj nunca llegan a la cadena del dashboard, ni el token del dashboard sirve aquí.
	 *
	 * @param http         constructor de reglas de Spring Security
	 * @param introspector reconoce el token del reloj
	 * @param noAutorizado respuesta 401 con el formato del contrato
	 * @return la cadena de filtros armada
	 */
	@Bean
	@Order(1)
	SecurityFilterChain filtrosDelReloj(HttpSecurity http, IntrospectorTokenReloj introspector,
			EntradaNoAutorizadaReloj noAutorizado) throws Exception {
		http
				.securityMatcher("/api/reloj/**")
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(rutas -> rutas.anyRequest().hasAuthority(PrincipalReloj.ROL))
				.exceptionHandling(errores -> errores.authenticationEntryPoint(noAutorizado))
				.oauth2ResourceServer(servidor -> servidor
						.authenticationEntryPoint(noAutorizado)
						.opaqueToken(opaco -> opaco.introspector(introspector)));
		return http.build();
	}

	/**
	 * Reglas del dashboard, que se aplican a cada petición antes de llegar al controlador.
	 *
	 * @param http constructor de reglas de Spring Security
	 * @return la cadena de filtros armada
	 */
	@Bean
	@Order(2)
	SecurityFilterChain filtrosDeSeguridad(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(rutas -> rutas
						.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
						// Quien activa su cuenta aún no tiene contraseña: lo autoriza el token del enlace
						.requestMatchers(HttpMethod.POST, "/api/auth/activar").permitAll()
						.requestMatchers("/actuator/health", "/error").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(servidor -> servidor.jwt(jwt -> jwt.jwtAuthenticationConverter(permisos())));
		return http.build();
	}

	/**
	 * Convierte el claim "permisos" del token en authorities de Spring, sin el prefijo "SCOPE_"
	 * que pone por defecto: así {@code hasAuthority('ALERTAS:VER')} coincide con el permiso tal cual.
	 */
	private static JwtAuthenticationConverter permisos() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(TokenJwt.CLAIM_PERMISOS);
		authorities.setAuthorityPrefix("");
		JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
		convertidor.setJwtGrantedAuthoritiesConverter(authorities);
		return convertidor;
	}

	/** @return bcrypt con el costo por defecto (10), para guardar y comparar contraseñas */
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/** @return el que firma los tokens al iniciar sesión (lo usa {@link TokenJwt}) */
	@Bean
	JwtEncoder jwtEncoder(PropiedadesSeguridad propiedades) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(clave(propiedades)));
	}

	/** @return el que revisa la firma y el vencimiento del token en cada petición */
	@Bean
	JwtDecoder jwtDecoder(PropiedadesSeguridad propiedades) {
		return NimbusJwtDecoder.withSecretKey(clave(propiedades)).macAlgorithm(MacAlgorithm.HS256).build();
	}

	/** La misma clave firma y verifica (HS256): por eso debe ser secreta y estar solo en el servidor. */
	private static SecretKey clave(PropiedadesSeguridad propiedades) {
		return new SecretKeySpec(propiedades.claveJwt().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

}
