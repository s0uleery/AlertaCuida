package cl.ucn.alertacuida.application.sesion;

import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.application.sesion.modelo.CuentaUsuario;
import cl.ucn.alertacuida.application.sesion.modelo.SesionIniciada;
import cl.ucn.alertacuida.application.sesion.modelo.TokenRenovacion;
import cl.ucn.alertacuida.application.sesion.modelo.UsuarioSesion;
import cl.ucn.alertacuida.application.sesion.puerto.CuentaUsuarioRepository;
import cl.ucn.alertacuida.application.sesion.puerto.EmisorToken;
import cl.ucn.alertacuida.application.sesion.puerto.TokensSesion;
import java.time.Instant;

/**
 * Entrega el par de tokens de una sesión; lo comparten el inicio de sesión y la renovación. Lee los
 * permisos en cada entrega, así que un cambio de permisos se nota en la siguiente renovación.
 */
public class AperturaSesion {

	private final CuentaUsuarioRepository cuentas;
	private final TokensSesion tokens;
	private final SecretosUnSoloUso secretos;
	private final EmisorToken emisor;
	private final PoliticaSesion politica;

	/** Recibe los puertos que usa; los arma ConfiguracionCasosDeUso. */
	public AperturaSesion(CuentaUsuarioRepository cuentas, TokensSesion tokens, SecretosUnSoloUso secretos,
			EmisorToken emisor, PoliticaSesion politica) {
		this.cuentas = cuentas;
		this.tokens = tokens;
		this.secretos = secretos;
		this.emisor = emisor;
		this.politica = politica;
	}

	/**
	 * Abre una sesión nueva tras un login correcto.
	 *
	 * @param cuenta quién inició sesión
	 * @param ahora  hora actual, desde la que corre la duración máxima
	 * @return los tokens y el usuario con sus permisos
	 */
	SesionIniciada abrir(CuentaUsuario cuenta, Instant ahora) {
		return continuar(cuenta, ahora, politica.vencimiento(ahora));
	}

	/**
	 * Entrega tokens nuevos sin mover el límite de la sesión.
	 *
	 * @param cuenta   dueña de la sesión
	 * @param ahora    hora actual
	 * @param expiraEn límite de la sesión
	 * @return los tokens y el usuario con sus permisos
	 */
	SesionIniciada continuar(CuentaUsuario cuenta, Instant ahora, Instant expiraEn) {
		String secreto = secretos.nuevo();
		tokens.guardar(cuenta.id(), secretos.hash(secreto), ahora, expiraEn);
		UsuarioSesion usuario = new UsuarioSesion(cuenta.id(), cuenta.nombre(), cuenta.codigoRol(),
				cuentas.permisosDelRol(cuenta.codigoRol()));
		return new SesionIniciada(emisor.emitir(usuario), usuario, new TokenRenovacion(secreto, expiraEn));
	}

}
