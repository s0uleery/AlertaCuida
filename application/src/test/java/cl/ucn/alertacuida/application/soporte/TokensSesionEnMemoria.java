package cl.ucn.alertacuida.application.soporte;

import cl.ucn.alertacuida.application.sesion.modelo.TokenSesion;
import cl.ucn.alertacuida.application.sesion.puerto.TokensSesion;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Doble de {@link TokensSesion}: guarda los tokens de renovación por su hash. */
public final class TokensSesionEnMemoria implements TokensSesion {

	private final Map<String, TokenSesion> porHash = new HashMap<>();

	@Override
	public void guardar(UUID idUsuario, String hashToken, Instant emision, Instant expiracion) {
		porHash.put(hashToken, new TokenSesion(UUID.randomUUID(), idUsuario, emision, expiracion, null));
	}

	@Override
	public Optional<TokenSesion> buscar(String hashToken) {
		return Optional.ofNullable(porHash.get(hashToken));
	}

	@Override
	public void revocar(UUID idToken, Instant fecha) {
		porHash.replaceAll((hash, t) -> t.id().equals(idToken) && t.revocadoEn() == null
				? new TokenSesion(t.id(), t.idUsuario(), t.emitidoEn(), t.expiraEn(), fecha)
				: t);
	}

	@Override
	public void eliminarTodos(UUID idUsuario) {
		porHash.values().removeIf(t -> t.idUsuario().equals(idUsuario));
	}

	@Override
	public void eliminar(String hashToken) {
		porHash.remove(hashToken);
	}

	@Override
	public void eliminarVencidos(Instant ahora) {
		porHash.values().removeIf(t -> !ahora.isBefore(t.expiraEn()));
	}

	/** @return cuántos tokens hay guardados, vigentes o no */
	public int cantidad() {
		return porHash.size();
	}

}
