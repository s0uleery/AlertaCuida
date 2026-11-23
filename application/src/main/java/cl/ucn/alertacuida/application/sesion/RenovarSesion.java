package cl.ucn.alertacuida.application.sesion;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.sesion.error.SesionNoRenovable;
import cl.ucn.alertacuida.application.sesion.modelo.CuentaUsuario;
import cl.ucn.alertacuida.application.sesion.modelo.SesionIniciada;
import cl.ucn.alertacuida.application.sesion.modelo.TokenSesion;
import cl.ucn.alertacuida.application.sesion.puerto.CuentaUsuarioRepository;
import cl.ucn.alertacuida.application.sesion.puerto.TokensSesion;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Cambia un token de renovación por tokens nuevos, sin pedir la contraseña.
 *
 * <p>Cada token sirve una sola vez (rotación). Si llega uno ya reemplazado, alguien pudo haberlo
 * copiado: se terminan todas las sesiones de la cuenta y queda en la auditoría. La excepción es la
 * gracia de unos segundos, porque dos pestañas pueden renovar a la vez con la misma cookie.
 */
public class RenovarSesion {

	private static final String ACCION_REUTILIZADO = "TOKEN_REUTILIZADO";

	private final TokensSesion tokens;
	private final CuentaUsuarioRepository cuentas;
	private final SecretosUnSoloUso secretos;
	private final AperturaSesion apertura;
	private final PoliticaSesion politica;
	private final RegistroAuditoria auditoria;
	private final Transacciones transacciones;
	private final Clock reloj;

	/** Recibe los puertos que usa; los arma ConfiguracionCasosDeUso. */
	public RenovarSesion(TokensSesion tokens, CuentaUsuarioRepository cuentas, SecretosUnSoloUso secretos,
			AperturaSesion apertura, PoliticaSesion politica, RegistroAuditoria auditoria, Transacciones transacciones,
			Clock reloj) {
		this.tokens = tokens;
		this.cuentas = cuentas;
		this.secretos = secretos;
		this.apertura = apertura;
		this.politica = politica;
		this.auditoria = auditoria;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/**
	 * @param secreto token de renovación que trae el navegador; puede faltar
	 * @param ip      desde dónde llegó la petición, para la auditoría
	 * @return tokens nuevos y el usuario con sus permisos actuales
	 * @throws SesionNoRenovable con el mismo mensaje para cualquier causa
	 */
	public SesionIniciada renovar(String secreto, String ip) {
		if (secreto == null || secreto.isBlank()) {
			throw new SesionNoRenovable();
		}
		Instant ahora = reloj.instant();
		// El error se lanza fuera de la transacción: terminar las sesiones debe quedar guardado
		return transacciones.ejecutar(() -> intentar(secreto, ip, ahora)).orElseThrow(SesionNoRenovable::new);
	}

	/** Revisa el token y, si sirve, lo reemplaza. Vacío = rechazado. */
	private Optional<SesionIniciada> intentar(String secreto, String ip, Instant ahora) {
		Optional<TokenSesion> encontrado = tokens.buscar(secretos.hash(secreto));
		if (encontrado.isEmpty()) {
			return Optional.empty();
		}
		TokenSesion token = encontrado.get();
		boolean yaReemplazado = token.revocadoEn() != null;
		if (yaReemplazado && !politica.enGracia(token, ahora)) {
			tokens.eliminarTodos(token.idUsuario());
			auditoria.registrar(token.idUsuario(), ACCION_REUTILIZADO, null, null, ip, ahora);
			return Optional.empty();
		}
		// Un token en gracia no mide inactividad: su reemplazo acaba de emitirse
		if (politica.vencida(token, ahora) || (!yaReemplazado && politica.inactiva(token, ahora))) {
			return Optional.empty();
		}
		Optional<CuentaUsuario> cuenta = cuentas.buscarPorId(token.idUsuario()).filter(CuentaUsuario::activo);
		if (cuenta.isEmpty()) {
			tokens.eliminarTodos(token.idUsuario());
			return Optional.empty();
		}
		if (!yaReemplazado) {
			tokens.revocar(token.id(), ahora);
		}
		return Optional.of(apertura.continuar(cuenta.get(), ahora, token.expiraEn()));
	}

}
