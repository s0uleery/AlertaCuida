package cl.ucn.alertacuida.adapters.seguridad;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
 * Seguridad del dashboard (1.3). Sin sesión en el servidor: cada petición trae el token en la cabecera
 * Authorization: Bearer. Por eso no hace falta CSRF (no hay cookies que el navegador mande solo).
 * Cada permiso del token ("ALERTAS:VER") es una authority: los endpoints usan hasAuthority (1.3.7).
 */
@Configuration
@EnableConfigurationProperties(PropiedadesSeguridad.class)
public class ConfiguracionSeguridad {

	@Bean
	SecurityFilterChain filtrosDeSeguridad(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(rutas -> rutas
						.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
						.requestMatchers("/actuator/health", "/error").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(servidor -> servidor.jwt(jwt -> jwt.jwtAuthenticationConverter(permisos())));
		return http.build();
	}

	private static JwtAuthenticationConverter permisos() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(TokenJwt.CLAIM_PERMISOS);
		authorities.setAuthorityPrefix("");
		JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
		convertidor.setJwtGrantedAuthoritiesConverter(authorities);
		return convertidor;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	JwtEncoder jwtEncoder(PropiedadesSeguridad propiedades) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(clave(propiedades)));
	}

	@Bean
	JwtDecoder jwtDecoder(PropiedadesSeguridad propiedades) {
		return NimbusJwtDecoder.withSecretKey(clave(propiedades)).macAlgorithm(MacAlgorithm.HS256).build();
	}

	private static SecretKey clave(PropiedadesSeguridad propiedades) {
		return new SecretKeySpec(propiedades.claveJwt().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

}
