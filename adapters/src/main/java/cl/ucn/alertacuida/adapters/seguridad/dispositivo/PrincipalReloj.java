package cl.ucn.alertacuida.adapters.seguridad.dispositivo;

import cl.ucn.alertacuida.application.asignacion.modelo.RelojAutenticado;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;

/**
 * El reloj autenticado, tal como lo ve Spring Security. Los controladores lo reciben con
 * {@code @AuthenticationPrincipal} y usan {@link #reloj()}.
 *
 * @param reloj el vínculo vigente del token
 */
public record PrincipalReloj(RelojAutenticado reloj) implements OAuth2AuthenticatedPrincipal {

	/** Única authority de un reloj: no tiene ningún permiso del dashboard. */
	public static final String ROL = "DEVICE";

	/** @return el vínculo, sin el token (no se guarda en ningún lado) */
	@Override
	public Map<String, Object> getAttributes() {
		return Map.of("idAsignacion", reloj.idAsignacion());
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority(ROL));
	}

	/** @return el identificador del vínculo, para los registros de Spring */
	@Override
	public String getName() {
		return reloj.idAsignacion().toString();
	}

}
